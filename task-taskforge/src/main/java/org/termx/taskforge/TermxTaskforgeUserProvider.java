package org.termx.taskforge;

import org.termx.taskforge.api.TaskforgeUserProvider;
import org.termx.taskforge.user.TaskforgeUser;
import com.kodality.termx.core.user.UserProvider;
import jakarta.inject.Singleton;
import java.util.List;
import lombok.RequiredArgsConstructor;


@Singleton
@RequiredArgsConstructor
public class TermxTaskforgeUserProvider extends TaskforgeUserProvider {
  private final UserProvider userProvider;

  @Override
  public List<TaskforgeUser> getUsers() {
    return userProvider.getUsers().stream().map(u -> new TaskforgeUser().setSub(u.getSub()).setName(u.getName())).toList();
  }
}
