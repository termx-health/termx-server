package org.termx.taskforge.task.execution;

import org.termx.taskforge.ApiError;
import org.termx.taskforge.auth.TaskforgeSessionProvider;
import org.termx.taskforge.task.TaskService;
import org.termx.taskforge.user.TaskforgeUser;
import jakarta.inject.Singleton;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class TaskExecutionService {
  private final TaskExecutionRepository taskExecutionRepository;
  private final TaskService taskService;
  private final TaskforgeSessionProvider session;

  public List<TaskExecution> loadAll(Long taskId) {
    taskService.validateAccess(taskId);
    return taskExecutionRepository.loadAll(taskId);
  }

  public TaskExecution save(TaskExecution ex) {
    taskService.validateAccess(ex.getTaskId());
    if (ex.getId() == null) {
      ex.setCreatedBy(new TaskforgeUser().setSub(session.require().getSub()));
      ex.setCreatedAt(OffsetDateTime.now());
    } else {
      TaskExecution current = taskExecutionRepository.load(ex.getId());
      if (!current.getTaskId().equals(ex.getTaskId())) {
        throw ApiError.TF102.toApiException();
      }
    }
    ex.setUpdatedAt(OffsetDateTime.now());
    ex.setUpdatedBy(new TaskforgeUser().setSub(session.require().getSub()));
    Long id = taskExecutionRepository.save(ex);
    return taskExecutionRepository.load(id);
  }
}
