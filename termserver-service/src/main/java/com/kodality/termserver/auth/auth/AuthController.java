package com.kodality.termserver.auth.auth;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.netty.cookies.NettyCookie;
import java.util.Collection;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import static com.kodality.termserver.auth.auth.OAuthSessionProvider.OAUTH_TOKEN_COOKIE;

@Controller("/auth")
@RequiredArgsConstructor
public class AuthController {

  private final UserPrivilegeStore userPrivilegeStore;

  @Post("/set-cookie")
  public HttpResponse<?> setCookie(HttpRequest<?> request) {
    Optional<String> token = request.getHeaders().getAuthorization();
    if (token.isPresent() && token.get().startsWith("Bearer ")) {
      return HttpResponse.ok().cookie(new NettyCookie(OAUTH_TOKEN_COOKIE, token.get()).path("/").maxAge(100));
    }
    return HttpResponse.ok();
  }

  @Get("/userinfo")
  public UserInfo getUserInfo() {
    SessionInfo sessionInfo = SessionStore.get().orElseThrow();
    Collection<String> privileges = userPrivilegeStore.getPrivileges(sessionInfo);
    return new UserInfo(sessionInfo.getUsername(), privileges);
  }

  @Getter
  @Setter
  @AllArgsConstructor
  @NoArgsConstructor
  public static class UserInfo {
    private String username;
    private Collection<String> privileges;
  }
}
