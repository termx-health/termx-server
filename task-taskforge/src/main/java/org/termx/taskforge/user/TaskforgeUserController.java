package org.termx.taskforge.user;

import org.termx.taskforge.api.TaskforgeUserProvider;
import io.micronaut.http.annotation.Get;
import io.micronaut.validation.Validated;
import java.util.List;
import jakarta.inject.Inject;

@Validated
public class TaskforgeUserController {
  @Inject
  private TaskforgeUserProvider userProvider;

  @Get()
  public List<TaskforgeUser> loadAll() {
    return userProvider.getUsers();
  }

}
