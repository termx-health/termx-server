package com.kodality.termx.core.sys.space;

import com.kodality.commons.exception.ApiClientException;
import com.kodality.termx.core.Privilege;
import com.kodality.termx.core.auth.Authorized;
import com.kodality.termx.core.github.GithubService.GithubDiff;
import com.kodality.termx.core.github.GithubService.GithubStatus;
import com.kodality.termx.core.sys.space.SpaceGithubService.SpaceGithubAuthResult;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;

@Controller("/spaces")
@RequiredArgsConstructor
public class SpaceGithubController {
  private final Optional<SpaceGithubService> spaceGithubService;

  private SpaceGithubService service() {
    return spaceGithubService.orElseThrow(() -> new ApiClientException("github not configured on the server"));
  }

  @Authorized(privilege = Privilege.S_VIEW)
  @Get("/github/providers")
  public Map<String, String> getProviders() {
    return spaceGithubService.map(SpaceGithubService::getProviders).orElse(Map.of());
  }

  @Authorized(Privilege.S_VIEW)
  @Post("/{id}/github/authenticate")
  public SpaceGithubAuthResult authenticate(@PathVariable Long id, @Body SpaceGithubAuthRequest r) {
    return service().authenticate(id, r.returnUrl);
  }

  @Authorized(Privilege.S_VIEW)
  @Get("/{id}/github/status")
  public GithubStatus prepareFiles(@PathVariable Long id) {
    return service().status(id);
  }

  @Authorized(Privilege.S_VIEW)
  @Get("/{id}/github/diff")
  public GithubDiff prepareFiles(@PathVariable Long id, @QueryValue String file) {
    return service().diff(id, file);
  }

  @Authorized(Privilege.S_EDIT)
  @Post("/{id}/github/push")
  public HttpResponse<?> push(@PathVariable Long id, @Body SpaceGithubCommitRequest req) {
    service().push(id, req.message, req.files);
    return HttpResponse.ok();
  }

  @Authorized(Privilege.S_EDIT)
  @Post("/{id}/github/pull")
  public HttpResponse<?> pull(@PathVariable Long id, @Body SpaceGithubPullRequest req) {
    service().pull(id, req.files);
    return HttpResponse.ok();
  }

  public record SpaceGithubAuthRequest(String returnUrl) {}

  public record SpaceGithubCommitRequest(String message, List<String> files) {}

  public record SpaceGithubPullRequest(List<String> files) {}
}
