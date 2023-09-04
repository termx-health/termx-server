package com.kodality.termx.sys.space;

import com.kodality.termx.auth.SessionStore;
import com.kodality.termx.github.GithubService;
import com.kodality.termx.github.GithubService.GithubCommit;
import com.kodality.termx.github.GithubService.GithubContent;
import com.kodality.termx.github.GithubService.GithubDiff;
import com.kodality.termx.github.GithubService.GithubStatus;
import io.micronaut.context.annotation.Requires;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

  public SpaceGithubAuthResult authenticate(Long spaceId, String returnUrl) {
    Space space = spaceService.load(spaceId);
    String repo = space.getIntegration().getGithub().getRepo();
    if (!githubService.isAuthorized(repo)) {
      return new SpaceGithubAuthResult(false, githubService.getAuthRedirect(returnUrl, repo));
    }
    return new SpaceGithubAuthResult(true, null);
  }

  public GithubStatus status(Long spaceId) {
    Space space = spaceService.load(spaceId);
    String repo = space.getIntegration().getGithub().getRepo();
    return dataHandlers.stream().map(h -> {
      return githubService.status(repo, h.getName(), getCurrentContent(spaceId, h));
    }).reduce(new GithubStatus(), (a, b) -> {
      a.setSha(b.getSha());
      a.getFiles().putAll(b.getFiles());
      return a;
    });
  }

  public GithubDiff diff(Long spaceId, String file) {
    Space space = spaceService.load(spaceId);
    String repo = space.getIntegration().getGithub().getRepo();
    String path = StringUtils.substringBefore(file, "/");
    SpaceGithubDataHandler h = dataHandlers.stream().filter(n -> n.getName().equals(path)).findFirst().orElseThrow();
    String name = StringUtils.substringAfter(file, "/");
    return new GithubDiff()
        .setLeft(githubService.getContent(repo, file).getContent())
        .setRight(h.getContent(spaceId).get(name));
  }

  public void push(Long spaceId, String message, List<String> files) {
    Space space = spaceService.load(spaceId);
    String repo = space.getIntegration().getGithub().getRepo();

    List<GithubContent> allChanges = dataHandlers.stream().flatMap(h -> {
          Map<String, GithubContent> changes = getCurrentContent(spaceId, h).stream().collect(Collectors.toMap(GithubContent::getPath, c -> c));
          GithubStatus status = githubService.status(repo, h.getName(), new ArrayList<>(changes.values()));
          return Stream.concat(
              status.getFiles().keySet().stream().filter(k -> List.of(GithubStatus.A, GithubStatus.M).contains(status.getFiles().get(k))).map(changes::get),
              status.getFiles().keySet().stream().filter(k -> GithubStatus.D.equals(status.getFiles().get(k))).map(p -> new GithubContent().setPath(p))
          );
        })
        .filter(c -> files == null || files.contains(c.getPath()))
        .toList();
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

  @Transactional
  public void pull(Long spaceId, List<String> files) {
    Space space = spaceService.load(spaceId);
    String repo = space.getIntegration().getGithub().getRepo();

    dataHandlers.forEach(h -> {
      List<GithubContent> changes = getCurrentContent(spaceId, h);
      GithubStatus status = githubService.status(repo, h.getName(), changes);
      Map<String, String> content = status.getFiles().keySet().stream()
          .filter(k -> List.of(GithubStatus.M, GithubStatus.A, GithubStatus.D).contains(status.getFiles().get(k)))
          .filter(k -> files == null || files.contains(k))
          .collect(com.kodality.commons.stream.Collectors.<String, String, String>toMap(
              k -> StringUtils.removeStart(k, h.getName() + "/"),
              k -> GithubStatus.A.equals(status.getFiles().get(k)) ? null : githubService.getContent(repo, k).getContent()
          ));
      h.saveContent(spaceId, content);
    });
  }

  private List<GithubContent> getCurrentContent(Long spaceId, SpaceGithubDataHandler h) {
    return h.getContent(spaceId).entrySet().stream().map(e -> new GithubContent().setPath(h.getName() + "/" + e.getKey()).setContent(e.getValue())).toList();
  }

  public record SpaceGithubAuthResult(boolean isAuthenticated, String redirectUrl) {}

}
