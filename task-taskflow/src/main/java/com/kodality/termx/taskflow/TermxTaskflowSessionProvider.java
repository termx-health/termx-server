package com.kodality.termx.taskflow;

import com.kodality.taskflow.auth.TaskflowSessionInfo;
import com.kodality.taskflow.auth.TaskflowSessionProvider;
import com.kodality.termx.core.auth.SessionStore;
import jakarta.inject.Singleton;
import java.util.Optional;

@Singleton
public class TermxTaskflowSessionProvider implements TaskflowSessionProvider {

  @Override
  public Optional<TaskflowSessionInfo> get() {
    return SessionStore.get().map(sessionInfo -> new TaskflowSessionInfo().setSub(sessionInfo.getUsername()).setTenant("1"));
  }
}
