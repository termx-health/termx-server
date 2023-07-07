package com.kodality.termx.user;

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

  @Get()
  public List<User> loadAll() {
    return userProvider.getUsers();
  }

}
