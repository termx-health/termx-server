package com.kodality.termx.github;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.QueryValue;
import java.net.URI;
import lombok.RequiredArgsConstructor;

@Requires(bean = GithubService.class)
@Controller("/github")
@RequiredArgsConstructor
public class GithubController {
  private final GithubService githubService;

  @Get("/cb")
  public HttpResponse<?> authCallback(@QueryValue String state, @QueryValue String code) {
    String redirectUri = githubService.authorizeUser(state, code);
    return HttpResponse.redirect(URI.create(redirectUri));
  }

  public record GithubState(boolean enabled){}

}
