package org.termx.taskforge.auth;

import java.util.Optional;

public interface TaskforgeSessionProvider {
  Optional<TaskforgeSessionInfo> get();

  default TaskforgeSessionInfo require() {
    return get().orElseThrow(() -> new IllegalStateException("Session required"));
  }

  default String requireTenant() {
    return get().map(TaskforgeSessionInfo::getTenant).orElseThrow(() -> new IllegalStateException("Tenant required"));
  }
}
