package com.kodality.termserver.auth.auth;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.netty.cookies.NettyCookie;
import java.util.Optional;

import static com.kodality.termserver.auth.auth.OAuthSessionProvider.OAUTH_TOKEN_COOKIE;

@Controller("/auth")
public class AuthController {

  @Post("/set-cookie")
  public HttpResponse<?> setCookie(HttpRequest<?> request) {
    Optional<String> token = request.getHeaders().getAuthorization();
    if (token.isPresent() && token.get().startsWith("Bearer ")) {
      return HttpResponse.ok().cookie(new NettyCookie(OAUTH_TOKEN_COOKIE, token.get()).path("/").maxAge(100));
    }
    return HttpResponse.ok();
  }
}
