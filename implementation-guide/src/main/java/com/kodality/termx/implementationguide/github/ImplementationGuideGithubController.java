package com.kodality.termx.implementationguide.github;

import com.kodality.termx.core.auth.Authorized;
import com.kodality.termx.core.github.GithubService.GithubDiff;
import com.kodality.termx.core.sys.space.SpaceGithubService.SpaceGithubAuthResult;
import com.kodality.termx.implementationguide.Privilege;
import com.kodality.termx.implementationguide.github.ImplementationGuideGithubService.IgGithubStatus;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
import java.util.List;
import lombok.RequiredArgsConstructor;

@Controller("/implementation-guides/{ig}/versions/{version}/github")
@RequiredArgsConstructor
public class ImplementationGuideGithubController {
  private final ImplementationGuideGithubService service;

  @Authorized(Privilege.IG_VIEW)
  @Post("/authenticate")
  public SpaceGithubAuthResult authenticate(@PathVariable String ig, @PathVariable String version, @Body IgGithubAuthRequest r) {
    return service.authenticate(ig, version, r.returnUrl);
  }

  @Authorized(Privilege.IG_VIEW)
  @Get("/status")
  public IgGithubStatus prepareFiles(@PathVariable String ig, @PathVariable String version) {
    return service.status(ig, version);
  }

  @Authorized(Privilege.IG_VIEW)
  @Get("/branches")
  public List<String> listBranches(@PathVariable String ig, @PathVariable String version) {
    return service.listBranches(ig, version);
  }

  @Authorized(Privilege.IG_VIEW)
  @Get("/diff")
  public GithubDiff diff(@PathVariable String ig, @PathVariable String version, @QueryValue String file) {
    return service.diff(ig, version, file);
  }

  @Authorized(Privilege.IG_EDIT)
  @Post("/push")
  public HttpResponse<?> push(@PathVariable String ig, @PathVariable String version, @Body IgGithubCommitRequest req) {
    service.push(ig, version, req.message);
    return HttpResponse.ok();
  }

  @Authorized(Privilege.IG_EDIT)
  @Post("/ig-initialize")
  public HttpResponse<?> initIg(@PathVariable String ig, @PathVariable String version) {
    service.initIg(ig, version);
    return HttpResponse.ok();
  }

  @Authorized(Privilege.IG_EDIT)
  @Post("/create-branch")
  public HttpResponse<?> createBranch(@PathVariable String ig, @PathVariable String version, @Body IgGithubCreateBranchRequest req) {
    service.createBranch(ig, version, req.baseBranch);
    return HttpResponse.ok();
  }

  public record IgGithubAuthRequest(String returnUrl) {}

  public record IgGithubCommitRequest(String message) {}

  public record IgGithubCreateBranchRequest(String baseBranch) {}
}
