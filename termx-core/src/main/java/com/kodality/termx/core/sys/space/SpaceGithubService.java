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
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.transaction.annotation.Transactional;

@Requires(bean = GithubService.class)
@Singleton
@RequiredArgsConstructor
public class SpaceGithubService {
  private static final String MAIN = "main";
  private final SpaceService spaceService;
  private final GithubService githubService;
  private final List<SpaceGithubDataHandler> dataHandlers;

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

    final Stream<GithubStatus> githubStatusStream = getHandlers(space).map(h -> {
      String dir = space.getIntegration().getGithub().getDirs().get(h.getName());
      return githubService.status(repo, MAIN, dir, h.getCurrentContent(space));
    });
    return githubStatusStream.reduce(new GithubStatus(), (a, b) -> {
      a.setSha(b.getSha());
      // path:status map
      final Map<String, String> filtered = b.getFiles().entrySet()
          .stream()
          .filter(getExcludeDeletedPredicate(a))
          .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
      a.getFiles().putAll(filtered);
      return a;
    });
  }

  public GithubDiff diff(Long spaceId, String file) {
    Space space = loadSpace(spaceId);
    String repo = space.getIntegration().getGithub().getRepo();

    Map<String, String> reverseDirs =
        space.getIntegration().getGithub().getDirs().entrySet().stream().collect(Collectors.toMap(Entry::getValue, Entry::getKey));

    // file may be folder, so try to match handler by longest dir
    String handlerType = reverseDirs.keySet().stream()
        .sorted((o1, o2) -> o2.length() - o1.length())
        .filter(file::startsWith)
        .map(reverseDirs::get)
        .findFirst()
        .orElse(null);

    String currentContent = dataHandlers.stream()
        .filter(n -> n.getName().equals(handlerType))
        .findFirst()
        .map(h -> h.getCurrentContent(space).stream().collect(Collectors.toMap(GithubContent::getPath, GithubContent::getContent)).get(file))
        .orElse(null);

    return new GithubDiff()
        .setLeft(getContent(file, repo))
        .setRight(currentContent);
  }

  private String getContent(String file, String repo) {
    final GithubContent resp = githubService.getContent(repo, MAIN, file);
    /*
      size > 1MByte
      https://docs.github.com/en/rest/repos/contents?apiVersion=2022-11-28#:~:text=endpoint%20are%20supported.-,Between%201%2D100%20MB,-%3A%20Only%20the%20raw
     */
    if (resp.getSize() > 1048576L) {
      return githubService.getBlob(resp.getGitUrl());
    }
    return resp.getContent();
  }

  public void push(Long spaceId, String message, List<String> files) {
    Space space = loadSpace(spaceId);
    String repo = space.getIntegration().getGithub().getRepo();

    List<GithubContent> allChanges = getHandlers(space).flatMap(h -> {
      String dir = space.getIntegration().getGithub().getDirs().get(h.getName());
      List<GithubContent> gc = h.getCurrentContent(space);
      return collectChanges(gc, githubService.status(repo, MAIN, dir, gc));
    }).filter(c -> files == null || files.contains(c.getPath())).toList();
    if (allChanges.isEmpty()) {
      return; // nothing changed
    }

    GithubCommit c = new GithubCommit();
    c.setMessage(message == null ? "update" : message);
    c.setAuthorName(SessionStore.require().getUsername());
    c.setAuthorEmail(SessionStore.require().getUsername() + "@termx"); // TODO email
    c.setFiles(allChanges);
    githubService.commit(repo, MAIN, c);
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
      GithubStatus status = githubService.status(repo, MAIN, dir, changes);
      Map<String, String> content = status.getFiles().keySet().stream()
          .filter(k -> List.of(GithubStatus.M, GithubStatus.A, GithubStatus.D).contains(status.getFiles().get(k)))
          .filter(k -> files == null || files.contains(k))
          .collect(com.kodality.commons.stream.Collectors.<String, String, String>toMap(
              k -> StringUtils.removeStart(k, dir + "/"),
              k -> GithubStatus.A.equals(status.getFiles().get(k)) ? null : getContent(k, repo)
          ));
      h.saveContent(spaceId, content);
    });
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


  private static Predicate<Entry<String, String>> getExcludeDeletedPredicate(GithubStatus accumulatedStatus) {
    /*
      NOTE: This filter is needed because multiple file entries with the same path can exist,
      with one marked as "D" (deleted) and another as "U" (unmodified) or "M" (modified).
      This occurs due to handling subfolders: GitTree returns files from both the root folder and subfolders,
      but the handler only works with files related to its configured folder. As a result, it is possible
      to get two entries for the same file with different statuses. Since the same file cannot be both
      existing and deleted simultaneously, and the order in which handlers are processed is unpredictable,
      we cannot guarantee that the correct entry will overwrite the incorrect one. Therefore, we need
      to remove "Deleted" entries if there is already an entry with another status.
     */
    return processingEntry -> {
      final String filePath = processingEntry.getKey();
      return !accumulatedStatus.getFiles().containsKey(filePath) || !processingEntry.getValue().equals("D");
    };
  }


  public record SpaceGithubAuthResult(boolean isAuthenticated, String redirectUrl) {}

  public record SpaceGithubIgStatus(boolean isInitialized) {}

}
