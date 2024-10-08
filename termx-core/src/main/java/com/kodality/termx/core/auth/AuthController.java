package com.kodality.termx.core.auth;

import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import java.util.Collection;
import lombok.RequiredArgsConstructor;

@Controller("/auth")
@RequiredArgsConstructor
public class AuthController {

  @Authorized
  @Get("/userinfo")
  public UserInfo getUserInfo() {
    return SessionStore.get().map(s -> new UserInfo(s.getUsername(), s.getPrivileges())).orElseThrow();
  }

  public record UserInfo(String username, Collection<String> privileges) {}
}
