package com.kodality.termx.core.user;

import com.kodality.termx.core.auth.Authorized;
import com.kodality.termx.core.auth.SessionInfo;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.validation.Validated;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

@Validated
@Controller("/users")
@RequiredArgsConstructor
public class UserController {
  private final UserProvider userProvider;

  @Authorized("*.Users.view")
  @Get("{?params*}")
  public List<User> loadAll(UserSearchParams params) {
    //TODO: auth
    return userProvider.getUsers().stream().filter(u -> filter(u, params)).toList();
  }

  private static boolean filter(User u, UserSearchParams params) {
    var sesh = new SessionInfo();
    sesh.setPrivileges(u.getPrivileges());

    boolean matches = true;
    if (StringUtils.isNotBlank(params.getRoles())) {
      // fixme: static method for privilege check?
      matches = matches && sesh.hasAnyPrivilege(List.of(params.getRoles().split(",")));
    }
    return matches;
  }


  @Getter
  @Setter
  public static class UserSearchParams {
    private String roles;
  }
}
