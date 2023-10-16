package com.kodality.termx.core.sys.space;

import com.kodality.termx.core.auth.SessionStore;
import com.kodality.termx.core.github.GithubService;
import com.kodality.termx.core.github.GithubService.GithubCommit;
import com.kodality.termx.core.github.GithubService.GithubContent;
import com.kodality.termx.core.github.GithubService.GithubDiff;
import com.kodality.termx.core.github.GithubService.GithubStatus;
import com.kodality.termx.sys.space.Space;
import io.micronaut.context.annotation.Requires;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.transaction.annotation.Transactional;

@Requires(bean = GithubService.class)
@Singleton
@RequiredArgsConstructor
public class SpaceGithubService {
  private final SpaceService spaceService;
  private final GithubService githubService;
  private final List<SpaceGithubDataHandler> dataHandlers;
  private final SpaceGithubDataImplementationGuideHandler igHandler;

  public Map<String, String> getProviders() {
    return dataHandlers.stream().collect(Collectors.toMap(SpaceGithubDataHandler::getName, SpaceGithubDataHandler::getDefaultDir));
  }

  public SpaceGithubAuthResult authenticate(Long spaceId, String returnUrl) {
    Space space = loadSpace(spaceId);
    String repo = space.getIntegration().getGithub().getRepo();
    if (!githubService.isAuthorized(repo)) {
      return new SpaceGithubAuthResult(false, githubService.getAuthRedirect(returnUrl, repo));
    }
    return new SpaceGithubAuthResult(true, null);
  }

  public GithubStatus status(Long spaceId) {
    Space space = loadSpace(spaceId);
    String repo = space.getIntegration().getGithub().getRepo();

    return Stream.concat(
        Stream.of(githubService.status(repo, igHandler.getCurrentContent(space))),
        getHandlers(space).map(h -> {
          String dir = space.getIntegration().getGithub().getDirs().get(h.getName());
          return githubService.status(repo, dir, h.getCurrentContent(space));
        })
    ).reduce(new GithubStatus(), (a, b) -> {
      a.setSha(b.getSha());
      a.getFiles().putAll(b.getFiles());
      return a;
    });
  }

  public GithubDiff diff(Long spaceId, String file) {
    Space space = loadSpace(spaceId);
    String repo = space.getIntegration().getGithub().getRepo();
    Map<String, String> reverseDirs =
        space.getIntegration().getGithub().getDirs().entrySet().stream().collect(Collectors.toMap(Entry::getValue, Entry::getKey));
    String path = file.contains("/") ? StringUtils.substringBeforeLast(file, "/") : "";
    String name = file.contains("/") ? StringUtils.substringAfterLast(file, "/") : file;
    String type = reverseDirs.get(path);
    String currentContent = dataHandlers.stream().filter(n -> n.getName().equals(type)).findFirst().map(h -> h.getContent(spaceId).get(name))
        .orElseGet(() -> igHandler.getContent(space).get(name));
    return new GithubDiff()
        .setLeft(githubService.getContent(repo, file).getContent())
        .setRight(currentContent);
  }

  public void push(Long spaceId, String message, List<String> files) {
    Space space = loadSpace(spaceId);
    String repo = space.getIntegration().getGithub().getRepo();

    List<GithubContent> allChanges =
        Stream.concat(
            Stream.of(space).map(s -> {
              List<GithubContent> gc = igHandler.getCurrentContent(space);
              return collectChanges(gc, githubService.status(repo, gc));
            }),
            getHandlers(space).map(h -> {
              String dir = space.getIntegration().getGithub().getDirs().get(h.getName());
              List<GithubContent> gc = h.getCurrentContent(space);
              return collectChanges(gc, githubService.status(repo, dir, gc));
            })
        ).flatMap(x -> x).toList();
    if (allChanges.isEmpty()) {
      return; // nothing changed
    }

    GithubCommit c = new GithubCommit();
    c.setMessage(message == null ? "update" : message);
    c.setAuthorName(SessionStore.require().getUsername());
    c.setAuthorEmail(SessionStore.require().getUsername() + "@termx"); // TODO email
    c.setFiles(allChanges);
    githubService.commit(repo, c);
  }

  private Stream<GithubContent> collectChanges(List<GithubContent> currentContent, GithubStatus status) {
    Map<String, GithubContent> changes = currentContent.stream().collect(Collectors.toMap(GithubContent::getPath, c -> c));
    return Stream.concat(
        status.getFiles().keySet().stream().filter(k -> List.of(GithubStatus.A, GithubStatus.M).contains(status.getFiles().get(k))).map(changes::get),
        status.getFiles().keySet().stream().filter(k -> GithubStatus.D.equals(status.getFiles().get(k))).map(p -> new GithubContent().setPath(p))
    );
  }

  @Transactional
  public void pull(Long spaceId, List<String> files) {
    Space space = loadSpace(spaceId);
    String repo = space.getIntegration().getGithub().getRepo();

    getHandlers(space).forEach(h -> {
      String dir = space.getIntegration().getGithub().getDirs().get(h.getName());
      List<GithubContent> changes = h.getCurrentContent(space);
      GithubStatus status = githubService.status(repo, dir, changes);
      Map<String, String> content = status.getFiles().keySet().stream()
          .filter(k -> List.of(GithubStatus.M, GithubStatus.A, GithubStatus.D).contains(status.getFiles().get(k)))
          .filter(k -> files == null || files.contains(k))
          .collect(com.kodality.commons.stream.Collectors.<String, String, String>toMap(
              k -> StringUtils.removeStart(k, dir + "/"),
              k -> GithubStatus.A.equals(status.getFiles().get(k)) ? null : githubService.getContent(repo, k).getContent()
          ));
      h.saveContent(spaceId, content);
    });
  }

  public SpaceGithubIgStatus getIgStatus(Long spaceId) {
    Space space = loadSpace(spaceId);
    if (space.getIntegration().getGithub().getIg() == null) {
      return new SpaceGithubIgStatus(true);
    }
    String repo = space.getIntegration().getGithub().getRepo();
    return new SpaceGithubIgStatus(githubService.getContent(repo, "_genonce.sh") != null);
  }

  public void initIg(Long spaceId, String baseRepo) {
    Space space = loadSpace(spaceId);
    String repo = space.getIntegration().getGithub().getRepo();

    List<GithubContent> contents = githubService.readFully(baseRepo).stream()
        .filter(c -> !List.of("LICENSE", "README.md", "sushi-config.yaml", "input/includes/menu.xml").contains(c.getPath()))
        .toList();
    GithubCommit c = new GithubCommit();
    c.setMessage("initialize ig from " + baseRepo);
    c.setAuthorName(SessionStore.require().getUsername());
    c.setAuthorEmail(SessionStore.require().getUsername() + "@termx"); // TODO email
    c.setFiles(contents);
    githubService.commit(repo, c);
  }

  private Space loadSpace(Long spaceId) {
    Space space = spaceService.load(spaceId);
    if (space.getIntegration() == null || space.getIntegration().getGithub() == null || space.getIntegration().getGithub().getRepo() == null) {
      throw new RuntimeException("github not configured");
    }
    if (space.getIntegration().getGithub().getDirs() == null) {
      space.getIntegration().getGithub().setDirs(Map.of());
    }
    return space;
  }

  private Stream<SpaceGithubDataHandler> getHandlers(Space space) {
    return dataHandlers.stream().filter(h -> space.getIntegration().getGithub().getDirs().containsKey(h.getName()));
  }

  public record SpaceGithubAuthResult(boolean isAuthenticated, String redirectUrl) {}

  public record SpaceGithubIgStatus(boolean isInitialized) {}

}
