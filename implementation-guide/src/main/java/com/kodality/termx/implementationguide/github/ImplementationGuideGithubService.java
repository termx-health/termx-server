package com.kodality.termx.implementationguide.github;

import com.kodality.commons.exception.NotFoundException;
import com.kodality.commons.util.PipeUtil;
import com.kodality.termx.core.auth.SessionStore;
import com.kodality.termx.core.github.GithubService;
import com.kodality.termx.core.github.GithubService.GithubCommit;
import com.kodality.termx.core.github.GithubService.GithubContent;
import com.kodality.termx.core.github.GithubService.GithubContent.GithubContentEncoding;
import com.kodality.termx.core.github.GithubService.GithubDiff;
import com.kodality.termx.core.github.GithubService.GithubStatus;
import com.kodality.termx.core.github.ResourceContentProvider;
import com.kodality.termx.core.sys.space.SpaceGithubService.SpaceGithubAuthResult;
import com.kodality.termx.implementationguide.ig.ImplementationGuide;
import com.kodality.termx.implementationguide.ig.ImplementationGuideService;
import com.kodality.termx.implementationguide.ig.version.ImplementationGuideVersion;
import com.kodality.termx.implementationguide.ig.version.ImplementationGuideVersionService;
import com.kodality.termx.implementationguide.ig.version.page.ImplementationGuidePage;
import com.kodality.termx.implementationguide.ig.version.page.ImplementationGuidePageService;
import com.kodality.termx.implementationguide.ig.version.resource.ImplementationGuideResource;
import com.kodality.termx.implementationguide.ig.version.resource.ImplementationGuideResourceService;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jakarta.inject.Singleton;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@Requires(bean = GithubService.class)
@Singleton
@RequiredArgsConstructor
public class ImplementationGuideGithubService {
  private final ImplementationGuideService igService;
  private final ImplementationGuideVersionService igVersionService;
  private final ImplementationGuideResourceService igResourceService;
  private final ImplementationGuidePageService igPageService;
  private final GithubService githubService;
  private final List<ResourceContentProvider> contentProviders;

  private static final String INPUT = "input";
  private static final String VOCABULARY = INPUT + "/vocabulary";
  private static final String PAGECONTENT = INPUT + "/pagecontent";
  //  private static final String FSH = INPUT + "/fsh";
  private static final Map<String, Map<String, String>> PATHS = Map.of(
      "CodeSystem", Map.of("fhir", VOCABULARY + "/code-systems"/*, "fsh", FSH + "/code-systems"*/),
      "ValueSet", Map.of("fhir", VOCABULARY + "/value-sets"/*, "fsh", FSH + "/value-sets"*/),
      "WikiPage", Map.of("md", PAGECONTENT)
  );

  public SpaceGithubAuthResult authenticate(String igId, String version, String returnUrl) {
    IgData ig = loadIg(igId, version);
    if (!githubService.isAuthorized(ig.repo)) {
      return new SpaceGithubAuthResult(false, githubService.getAuthRedirect(returnUrl, ig.repo));
    }
    return new SpaceGithubAuthResult(true, null);
  }

  public IgGithubStatus status(String igId, String version) {
    IgData ig = loadIg(igId, version);
    IgGithubStatus st = new IgGithubStatus();
    st.setBranchExists(githubService.listBranches(ig.repo).contains(ig.branch));
    if (st.isBranchExists()) {
      st.setIgInitialized(isIgInitialized(ig.version));
    }
    if (st.isIgInitialized()) {
      GithubStatus rootSt = githubService.status(ig.repo, ig.branch, getRootContent(ig.ig, ig.version));
      GithubStatus resourcesSt = githubService.status(ig.repo, ig.branch, INPUT, getResourcesContent(ig.version));
      st.setSha(rootSt.getSha());
      st.getFiles().putAll(rootSt.getFiles());
      st.getFiles().putAll(resourcesSt.getFiles());
    }
    return st;
  }

  public List<String> listBranches(String igId, String version) {
    IgData ig = loadIg(igId, version);
    return githubService.listBranches(ig.repo);
  }

  public GithubDiff diff(String igId, String version, String file) {
    IgData ig = loadIg(igId, version);
    String currentContent = Stream.concat(
            getRootContent(ig.ig, ig.version).stream(),
            getResourcesContent(ig.version).stream()
        )
        .filter(c -> c.getPath().equals(file))
        .findFirst()
        .map(GithubContent::getContent)
        .orElse(null);

    return new GithubDiff()
        .setLeft(githubService.getContent(ig.repo, ig.branch, file).getContent())
        .setRight(currentContent);
  }

  public void push(String igId, String version, String message) {
    IgData ig = loadIg(igId, version);
    List<GithubContent> resourceContents = getResourcesContent(ig.version);
    List<GithubContent> rootContents = getRootContent(ig.ig, ig.version);

    List<GithubContent> allChanges = Stream.concat(
        collectChanges(rootContents, githubService.status(ig.repo, ig.branch, rootContents)),
        collectChanges(resourceContents, githubService.status(ig.repo, ig.branch, INPUT, resourceContents))
    ).toList();

    if (allChanges.isEmpty()) {
      return; // nothing changed
    }

    GithubCommit c = new GithubCommit();
    c.setMessage(message == null ? "update" : message);
    c.setAuthorName(SessionStore.require().getUsername());
    c.setAuthorEmail(SessionStore.require().getUsername() + "@termx"); // TODO email
    c.setFiles(allChanges);
    githubService.commit(ig.repo, ig.branch, c);
  }

  private List<GithubContent> getRootContent(ImplementationGuide ig, ImplementationGuideVersion igVersion) {
    return List.of(
        new GithubContent().setPath("sushi-config.yaml").setContent(generateSushiConfig(ig, igVersion)).setEncoding(GithubContentEncoding.utf8),
        new GithubContent().setPath("ig.ini").setContent(generateIgIni(ig, igVersion)).setEncoding(GithubContentEncoding.utf8)
    );
  }

  private List<GithubContent> getResourcesContent(ImplementationGuideVersion igVersion) {
    List<ImplementationGuideResource> resources = igResourceService.loadAll(igVersion.getId());
    List<ImplementationGuidePage> pages = igPageService.loadAll(igVersion.getId());
    return Stream.concat(
        resources.stream().flatMap(r -> {
          Map<String, String> paths = PATHS.get(r.getType());
          return paths == null ? Stream.empty() : paths.keySet().stream().flatMap(p -> {
            return getProvider(r.getType(), p).getContent(PipeUtil.toPipe(r.getReference(), r.getVersion())).stream().map(content -> {
              return new GithubContent()
                  .setPath(paths.get(p) + "/" + content.getName())
                  .setContent(content.getContent())
                  .setEncoding(content.getEncoding());
            });
          });
        }),
        pages.stream().flatMap(p -> {
          String path = PATHS.get("WikiPage").get("md");
          return getProvider("WikiPage", "md").getContent(PipeUtil.toPipe(p.getSpace().getId().toString(), p.getPage())).stream().map(content -> {
            return new GithubContent()
                .setPath(path + "/" + content.getName())
                .setContent(content.getContent())
                .setEncoding(content.getEncoding());
          });
        })
    ).toList();
  }

  private Stream<GithubContent> collectChanges(List<GithubContent> currentContent, GithubStatus status) {
    Map<String, GithubContent> changes = currentContent.stream().collect(Collectors.toMap(GithubContent::getPath, c -> c));
    return Stream.concat(
        status.getFiles().keySet().stream().filter(k -> List.of(GithubStatus.A, GithubStatus.M).contains(status.getFiles().get(k))).map(changes::get),
        status.getFiles().keySet().stream().filter(k -> GithubStatus.D.equals(status.getFiles().get(k))).map(p -> new GithubContent().setPath(p))
    );
  }

  private boolean isIgInitialized(ImplementationGuideVersion igVersion) {
    String repo = igVersion.getGithub().getRepo();
    String branch = igVersion.getGithub().getBranch() == null ? "main" : igVersion.getGithub().getBranch();
    return githubService.getContent(repo, branch, "_genonce.sh") != null;
  }

  public void initIg(String igId, String version) {
    IgData ig = loadIg(igId, version);
    List<GithubContent> contents = githubService.readFully(ig.version.getGithub().getInit()).stream()
        .filter(c -> !List.of("LICENSE", "README.md", "sushi-config.yaml", "input/includes/menu.xml", "ig.ini").contains(c.getPath()))
        .toList();
    GithubCommit c = new GithubCommit();
    c.setMessage("initialize ig from " + ig.version.getGithub().getInit());
    c.setAuthorName(SessionStore.require().getUsername());
    c.setAuthorEmail(SessionStore.require().getUsername() + "@termx"); // TODO email
    c.setFiles(contents);
    githubService.commit(ig.repo, ig.branch, c);
  }

  public void createBranch(String igId, String igVersion, String baseBranch) {
    IgData ig = loadIg(igId, igVersion);
    String sha = githubService.getLastCommitSha(ig.repo, baseBranch);
    githubService.createBranch(ig.repo, ig.branch, sha);
  }

  private String generateIgIni(ImplementationGuide ig, ImplementationGuideVersion igVersion) {
    List<String> parts = new ArrayList<>();
    parts.add("[IG]");
    parts.add("ig = fsh-generated/resources/ImplementationGuide-" + ig.getId() + ".json");
    parts.add("template = fhir.base.template#current");
    return String.join("\n", parts);
  }

  private String generateSushiConfig(ImplementationGuide ig, ImplementationGuideVersion igVersion) {
    List<String> parts = new ArrayList<>();
    parts.add("id: " + ig.getId());
    parts.add("canonical: " + ig.getUri());
    parts.add("name: " + ig.getTitle().get("en"));
    parts.add("title: " + ig.getTitle().get("en"));
    if (StringUtils.isNotEmpty(ig.getDescription().get("en"))) {
      parts.add("description: " + ig.getDescription().get("en"));
    }
    parts.add("status: " + igVersion.getStatus());
    parts.add("version: " + igVersion.getVersion());
//    parts.add("fhirVersion: " + igVersion.getFhirVersion());
    parts.add("fhirVersion: 5.0.0");
    parts.add("copyrightYear: 2024+");
    parts.add("releaseLabel: ballot");
    if (ig.getPublisher() != null) {
      parts.add("publisher: ");
      parts.add("  name: " + ig.getPublisher());
    }

    List<ImplementationGuidePage> pages = igPageService.loadAll(igVersion.getId());
    if (CollectionUtils.isNotEmpty(pages)) {
      parts.add("");
      parts.add("pages:");
      pages.forEach(pc -> {
        parts.add("  " + pc.getPage() + ".md:");
        parts.add("    title: " + pc.getName());
      });
    }
//    if (CollectionUtils.isNotEmpty(ig.getMenu())) {
//      parts.add("");
//      parts.add("menu:");
//      ig.getMenu().forEach(m -> {
//        parts.add("  " + m.getName() + ": " + (m.getPage() != null ? pages.get(m.getPage()).getSlug() + ".html" : ""));
//        if (m.getChildren() != null) {
//          m.getChildren().forEach(c -> {
//            parts.add("    " + c.getName() + ": " + pages.get(c.getPage()).getSlug() + ".html");
//          });
//        }
//      });
//    }
    if (CollectionUtils.isNotEmpty(pages)) {
      parts.add("");
      parts.add("menu:");
      pages.forEach(pc -> parts.add("  " + pc.getName() + ": " + pc.getPage() + ".html"));
    }

    return String.join("\n", parts);
  }

  private ResourceContentProvider getProvider(String resourceType, String contentType) {
    return contentProviders.stream().filter(cp -> cp.getResourceType().equals(resourceType) && cp.getContentType().equals(contentType))
        .findFirst().orElseThrow();
  }

  private IgData loadIg(String igId, String version) {
    ImplementationGuide ig = igService.load(igId).orElseThrow(() -> new NotFoundException("ImplementationGuide not found: " + igId));
    ImplementationGuideVersion igVersion =
        igVersionService.load(igId, version).orElseThrow(() -> new NotFoundException("ImplementationGuideVersion not found: " + igId + " " + version));
    String repo = igVersion.getGithub().getRepo();
    String branch = igVersion.getGithub().getBranch() == null ? "main" : igVersion.getGithub().getBranch();
    return new IgData(ig, igVersion, repo, branch);
  }

  private record IgData(ImplementationGuide ig, ImplementationGuideVersion version, String repo, String branch) {}

  @Getter
  @Setter
  public static class IgGithubStatus extends GithubStatus {
    private boolean igInitialized;
    private boolean branchExists;
  }

}
