package com.kodality.termx.sys.space;

import com.kodality.commons.exception.ApiClientException;
import com.kodality.termx.Privilege;
import com.kodality.termx.auth.Authorized;
import com.kodality.termx.github.GithubService.GithubStatus;
import com.kodality.termx.sys.space.SpaceGithubService.SpaceGithubAuthResult;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import java.util.Optional;
import lombok.RequiredArgsConstructor;

@Controller("/spaces/{id}/github")
@RequiredArgsConstructor
public class SpaceGithubController {
  private final Optional<SpaceGithubService> spaceGithubService;

  private SpaceGithubService service() {
    return spaceGithubService.orElseThrow(() -> new ApiClientException("github not configured on the server"));
  }

  @Authorized(Privilege.P_VIEW)
  @Post("/authenticate")
  public SpaceGithubAuthResult authenticate(@Parameter Long id, @Body SpaceGithubAuthRequest r) {
    return service().authenticate(id, r.returnUrl);
  }

  @Authorized(Privilege.P_VIEW)
  @Get("/status")
  public GithubStatus prepareFiles(@Parameter Long id) {
    return service().status(id);
  }

  @Authorized(Privilege.P_EDIT)
  @Post("/push")
  public HttpResponse<?> push(@Parameter Long id, @Body SpaceGithubCommitRequest req) {
    service().push(id, req.message);
    return HttpResponse.ok();
  }

  public record SpaceGithubAuthRequest(String returnUrl) {}
  public record SpaceGithubCommitRequest(String message) {}
}
