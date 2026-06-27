package org.termx.core.sys.space;

import org.termx.core.Privilege;
import org.termx.core.auth.Authorized;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;

/**
 * Placeholder MS DevOps (Azure) space integration endpoints, mirroring {@link SpaceGithubController}.
 *
 * <p>These return empty / no-op responses so the (feature-flagged) MS DevOps UI in termx-web does not hit
 * 404s while the real Azure DevOps backend is implemented. The {@code authenticate} stub reports
 * {@code isAuthenticated = true} so the UI proceeds to load an (empty) status instead of redirecting.
 *
 * <p>Authorization mirrors the GitHub controller: read for providers/authenticate/status/diff,
 * write for push/pull.
 */
@Controller("/spaces")
@RequiredArgsConstructor
public class SpaceMsDevOpsController {

  @Authorized(privilege = Privilege.S_READ)
  @Get("/msdevops/providers")
  public Map<String, String> getProviders() {
    return Map.of();
  }

  @Authorized(Privilege.S_READ)
  @Post("/{id}/msdevops/authenticate")
  public MsDevOpsAuthResult authenticate(@PathVariable Long id, @Body MsDevOpsAuthRequest r) {
    return new MsDevOpsAuthResult(true, null);
  }

  @Authorized(Privilege.S_READ)
  @Get("/{id}/msdevops/status")
  public MsDevOpsStatus status(@PathVariable Long id) {
    return new MsDevOpsStatus(null, Map.of());
  }

  @Authorized(Privilege.S_READ)
  @Get("/{id}/msdevops/diff")
  public MsDevOpsDiff diff(@PathVariable Long id, @QueryValue String file) {
    return new MsDevOpsDiff(null, null);
  }

  @Authorized(Privilege.S_WRITE)
  @Post("/{id}/msdevops/push")
  public HttpResponse<?> push(@PathVariable Long id, @Body MsDevOpsCommitRequest req) {
    return HttpResponse.ok();
  }

  @Authorized(Privilege.S_WRITE)
  @Post("/{id}/msdevops/pull")
  public HttpResponse<?> pull(@PathVariable Long id, @Body MsDevOpsPullRequest req) {
    return HttpResponse.ok();
  }

  public record MsDevOpsAuthRequest(String returnUrl) {}

  public record MsDevOpsAuthResult(boolean isAuthenticated, String redirectUrl) {}

  public record MsDevOpsStatus(String sha, Map<String, String> files) {}

  public record MsDevOpsDiff(String left, String right) {}

  public record MsDevOpsCommitRequest(String message, List<String> files) {}

  public record MsDevOpsPullRequest(List<String> files) {}
}
