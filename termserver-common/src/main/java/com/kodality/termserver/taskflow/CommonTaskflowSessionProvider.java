package com.kodality.termserver.taskflow;

import com.kodality.taskflow.auth.TaskflowSessionInfo;
import com.kodality.taskflow.auth.TaskflowSessionProvider;
import com.kodality.termserver.auth.SessionStore;
import java.util.Optional;
import javax.inject.Singleton;

@Singleton
public class CommonTaskflowSessionProvider implements TaskflowSessionProvider {

  @Override
  public Optional<TaskflowSessionInfo> get() {
    return SessionStore.get().map(sessionInfo -> new TaskflowSessionInfo().setSub(sessionInfo.getUsername()).setTenant("1"));
  }
}
