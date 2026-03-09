package org.termx.taskforge.task;

import org.termx.taskforge.api.TaskInterceptor;
import org.termx.taskforge.task.Task;
import org.termx.task.api.TaskStatusChangeInterceptor;
import jakarta.inject.Singleton;
import java.util.List;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class TaskChangeInterceptor implements TaskInterceptor {
  private final List<TaskStatusChangeInterceptor> interceptors;
  private final TaskMapper taskMapper;

  @Override
  public void beforeChange(String event, Task task, Task previous) {}

  @Override
  public void afterChange(String event, Task task, Task previous) {
    if (previous != null && !task.getStatus().equals(previous.getStatus())) {
      interceptors.forEach(i -> i.afterStatusChange(taskMapper.map(task), previous.getStatus()));
    }
  }
}
