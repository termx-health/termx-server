package org.termx.taskforge.task.activity;

import com.kodality.commons.exception.NotFoundException;
import org.termx.taskforge.auth.TaskforgeSessionProvider;
import org.termx.taskforge.task.TaskService;
import org.termx.taskforge.user.TaskforgeUser;
import jakarta.inject.Singleton;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class TaskActivityService {
  private final TaskActivityRepository taskActivityRepository;
  private final TaskService taskService;
  private final TaskforgeSessionProvider session;

  public TaskActivity load(Long activityId) {
    TaskActivity activity = taskActivityRepository.load(activityId);
    if (activity == null) {
      throw new NotFoundException("task activity", activityId);
    }
    taskService.validateAccess(activity.getTaskId());
    return activity;
  }

  public List<TaskActivity> search(TaskActivitySearchParams params) {
    return taskActivityRepository.search(params, session.requireTenant());
  }

  public TaskActivity save(TaskActivity ta) {
    Objects.requireNonNull(ta.getTaskId());
    taskService.validateAccess(ta.getTaskId());
    ta.setUpdatedAt(OffsetDateTime.now());
    ta.setUpdatedBy(new TaskforgeUser().setSub(session.require().getSub()));
    taskActivityRepository.save(ta);
    return ta;
  }

  public void cancel(Long activityId) {
    taskActivityRepository.cancel(activityId);
  }
}
