package com.kodality.termx.sys.space;

import com.kodality.commons.exception.ApiClientException;
import com.kodality.termx.Privilege;
import com.kodality.termx.auth.Authorized;
import com.kodality.termx.github.GithubService.GithubDiff;
import com.kodality.termx.github.GithubService.GithubStatus;
import com.kodality.termx.sys.space.SpaceGithubService.SpaceGithubAuthResult;
import com.kodality.termx.sys.space.SpaceGithubService.SpaceGithubIgStatus;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
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

  @Authorized(Privilege.P_VIEW)
  @Get("/github/providers")
  public Map<String, String> getProviders() {
    return service().getProviders();
  }

  @Authorized(Privilege.P_VIEW)
  @Post("/{id}/github/authenticate")
  public SpaceGithubAuthResult authenticate(@Parameter Long id, @Body SpaceGithubAuthRequest r) {
    return service().authenticate(id, r.returnUrl);
  }

  @Authorized(Privilege.P_VIEW)
  @Get("/{id}/github/status")
  public GithubStatus prepareFiles(@Parameter Long id) {
    return service().status(id);
  }

  @Authorized(Privilege.P_VIEW)
  @Get("/{id}/github/diff")
  public GithubDiff prepareFiles(@Parameter Long id, @QueryValue String file) {
    return service().diff(id, file);
  }

  @Authorized(Privilege.P_EDIT)
  @Post("/{id}/github/push")
  public HttpResponse<?> push(@Parameter Long id, @Body SpaceGithubCommitRequest req) {
    service().push(id, req.message, req.files);
    return HttpResponse.ok();
  }

  @Authorized(Privilege.P_EDIT)
  @Post("/{id}/github/pull")
  public HttpResponse<?> pull(@Parameter Long id, @Body SpaceGithubPullRequest req) {
    service().pull(id, req.files);
    return HttpResponse.ok();
  }

  @Authorized(Privilege.P_EDIT)
  @Post("/{id}/github/ig-initialize")
  public HttpResponse<?> initIg(@Parameter Long id, @Body SpaceGithubIgInitRequest req) {
    service().initIg(id, req.base);
    return HttpResponse.ok();
  }

  @Authorized(Privilege.P_VIEW)
  @Get("/{id}/github/ig-status")
  public SpaceGithubIgStatus getIgStatus(@Parameter Long id) {
    return service().getIgStatus(id);
  }

  public record SpaceGithubAuthRequest(String returnUrl) {}

  public record SpaceGithubCommitRequest(String message, List<String> files) {}

  public record SpaceGithubPullRequest(List<String> files) {}

  public record SpaceGithubIgInitRequest(String base) {}
}
