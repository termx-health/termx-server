package com.kodality.termserver.auth.auth;

import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import java.util.Collection;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@Controller("/auth")
@RequiredArgsConstructor
public class AuthController {

  private final UserPrivilegeStore userPrivilegeStore;

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
