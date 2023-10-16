package com.kodality.termx.core.user;

import com.kodality.termx.core.auth.Authorized;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.validation.Validated;
import java.util.List;
import lombok.RequiredArgsConstructor;

@Validated
@Controller("/users")
@RequiredArgsConstructor
public class UserController {
  private final UserProvider userProvider;

  @Authorized("*.Users.view")
  @Get()
  public List<User> loadAll() {
    //TODO: auth
    return userProvider.getUsers();
  }

}
