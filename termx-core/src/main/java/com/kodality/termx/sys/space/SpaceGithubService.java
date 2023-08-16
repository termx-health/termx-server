package com.kodality.termx.sys.space;

import com.kodality.termx.auth.SessionStore;
import com.kodality.termx.github.GithubService;
import com.kodality.termx.github.GithubService.GithubCommit;
import com.kodality.termx.github.GithubService.GithubContent;
import com.kodality.termx.github.GithubService.GithubStatus;
import com.kodality.termx.wiki.PageProvider;
import com.kodality.termx.wiki.page.PageContent;
import io.micronaut.context.annotation.Requires;
import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Requires(bean = GithubService.class)
@Singleton
@RequiredArgsConstructor
public class SpaceGithubService {
  private final SpaceService spaceService;
  private final PageProvider pageProvider;
  private final GithubService githubService;

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
    return githubService.status(repo, "wiki", collectWiki(spaceId));
  }

  public void push(Long spaceId, String message) {
    Space space = spaceService.load(spaceId);
    String repo = space.getIntegration().getGithub().getRepo();

    List<GithubContent> changes = collectWiki(spaceId);
    GithubStatus status = githubService.status(repo, "wiki", changes);
    if (status.getFiles().isEmpty() || status.getFiles().values().stream().distinct().allMatch(s -> GithubStatus.U.equals(s))) {
      // nothing changed
      return;
    }
    changes.addAll(status.getFiles().keySet().stream()
        .filter(k -> GithubStatus.D.equals(status.getFiles().get(k)))
        .map(p -> new GithubContent().setPath(p)).toList());

    GithubCommit c = new GithubCommit();
    c.setMessage(message == null ? "update" : message);
    c.setAuthorName(SessionStore.require().getUsername());
    c.setAuthorEmail(SessionStore.require().getUsername() + "@termx"); // TODO email
    c.setFiles(changes);
    githubService.commit(repo, c);
  }

  private List<GithubContent> collectWiki(Long spaceId) {
    List<PageContent> pages = pageProvider.findPages(spaceId);
    return pages.stream().map(p -> new GithubContent()
        .setContent(p.getContent())
        .setPath("wiki/" + p.getSlug() + "." + p.getLang() + ".md")
    ).collect(Collectors.toList());
  }

  public record SpaceGithubAuthResult(boolean isAuthenticated, String redirectUrl) {}

}
