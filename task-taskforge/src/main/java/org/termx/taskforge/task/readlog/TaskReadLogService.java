package org.termx.taskforge.task.readlog;

import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class TaskReadLogService {
  private final TaskReadLogRepository repository;

  public void logTaskRead(Long taskId, String userId) {
    repository.upsert(taskId, userId);
  }
}
