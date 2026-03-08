package org.termx.taskforge;

import org.termx.taskforge.auth.TaskforgeSessionInfo;
import org.termx.taskforge.auth.TaskforgeSessionProvider;
import com.kodality.termx.core.auth.SessionStore;
import jakarta.inject.Singleton;
import java.util.Optional;

@Singleton
public class TermxTaskforgeSessionProvider implements TaskforgeSessionProvider {

  @Override
  public Optional<TaskforgeSessionInfo> get() {
    return SessionStore.get().map(sessionInfo -> new TaskforgeSessionInfo().setSub(sessionInfo.getUsername()).setTenant("1"));
  }
}
