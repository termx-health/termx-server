package com.kodality.termserver.github;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@Controller("/github")
@RequiredArgsConstructor
public class GithubController {
  private final GithubService githubService;

  @Get("/cb")
  public HttpResponse<?> authCallback(@QueryValue String state, @QueryValue String code) throws URISyntaxException {
    String redirectUri = githubService.authorizeUser(state, code);
    return HttpResponse.redirect(new URI(redirectUri));
  }

  @Post("/authorize")
  public HttpResponse<?> authorize(@Body UserState state) {
    String redirectUri = githubService.getAuthRedirect(state.state);
    return HttpResponse.ok(Map.of("redirectUri", redirectUri));
  }

  @Get(value = "/installations", produces = MediaType.APPLICATION_JSON)
  public HttpResponse<?> getInstallations() {
    return HttpResponse.ok(githubService.getInstallations());
  }

  @Get(value = "/repositories", produces = MediaType.APPLICATION_JSON)
  public HttpResponse<?> getRepos(@QueryValue String installationId) {
    return HttpResponse.ok(githubService.getRepositories(installationId));
  }

  @Post(value = "/export", produces = MediaType.APPLICATION_JSON)
  public HttpResponse<?> export(@Body ExportData data) {
    return HttpResponse.ok(githubService.export(data));
  }

  @Getter
  @Setter
  public static class ExportData {
    private String repoUrl;
    private String path;
    private String message;
    private String content;
  }

  @Getter
  @Setter
  public static class UserState {
    private String state;
  }
}
