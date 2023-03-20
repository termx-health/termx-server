package com.kodality.termserver.auth.auth;

import com.kodality.termserver.auth.SessionInfo;
import com.kodality.termserver.auth.SessionStore;
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

  @Get("/userinfo")
  public UserInfo getUserInfo() {
    SessionInfo sessionInfo = SessionStore.get().orElseThrow();
    return new UserInfo(sessionInfo.getUsername(), sessionInfo.getPrivileges());
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
