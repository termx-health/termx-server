package com.kodality.termx.taskflow;

import com.kodality.taskflow.api.TaskflowUserProvider;
import com.kodality.taskflow.user.TaskflowUser;
import com.kodality.termx.core.user.UserProvider;
import jakarta.inject.Singleton;
import java.util.List;
import lombok.RequiredArgsConstructor;


@Singleton
@RequiredArgsConstructor
public class TermxTaskflowUserProvider extends TaskflowUserProvider {
  private final UserProvider userProvider;

  @Override
  public List<TaskflowUser> getUsers() {
    return userProvider.getUsers().stream().map(u -> new TaskflowUser().setSub(u.getSub()).setName(u.getName())).toList();
  }
}
