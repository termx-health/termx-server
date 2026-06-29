package org.termx.core.msdevops;

import com.kodality.commons.exception.ApiClientException;
import org.termx.core.Privilege;
import org.termx.core.auth.Authorized;
import org.termx.core.github.GithubService.GithubDiff;
import org.termx.core.github.GithubService.GithubStatus;
import org.termx.core.sys.space.SpaceGithubService;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.*;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Controller("/spaces")
@RequiredArgsConstructor
public class SpaceMsDevopsController {
  private final Optional<SpaceMsDevopsService> spaceMsDevopsService;

  private SpaceMsDevopsService service() {
    return spaceMsDevopsService.orElseThrow(() -> new ApiClientException("MsDevops not configured on the server"));
  }

  @Authorized(privilege = Privilege.S_READ)
  @Get("/msdevops/providers")
  public Map<String, String> getProviders() {
    return spaceMsDevopsService.map(SpaceMsDevopsService::getProviders).orElse(Map.of());
  }

  @Authorized(Privilege.S_READ)
  @Post("/{id}/msdevops/authenticate")
  public SpaceGithubService.SpaceGithubAuthResult authenticate(@PathVariable Long id, @Body SpaceGithubAuthRequest r) {
    return service().authenticate(id, r.returnUrl);
  }

  @Authorized(Privilege.S_READ)
  @Get("/{id}/msdevops/status")
  public GithubStatus prepareFiles(@PathVariable Long id) {
    return service().status(id);
  }

  @Authorized(Privilege.S_READ)
  @Get("/{id}/msdevops/diff")
  public GithubDiff prepareFiles(@PathVariable Long id, @QueryValue String file) {
    return service().diff(id, file);
  }

  @Authorized(Privilege.S_WRITE)
  @Post("/{id}/msdevops/push")
  public HttpResponse<?> push(@PathVariable Long id, @Body SpaceGithubCommitRequest req) {
    service().push(id, req.message, req.files);
    return HttpResponse.ok();
  }

  @Authorized(Privilege.S_WRITE)
  @Post("/{id}/msdevops/pull")
  public HttpResponse<?> pull(@PathVariable Long id, @Body SpaceGithubPullRequest req) {
    service().pull(id, req.files);
    return HttpResponse.ok();
  }

  public record SpaceGithubAuthRequest(String returnUrl) {}

  public record SpaceGithubCommitRequest(String message, List<String> files) {}

  public record SpaceGithubPullRequest(List<String> files) {}
}
