package com.kodality.termx.taskflow.task;

import com.kodality.taskflow.api.TaskInterceptor;
import com.kodality.taskflow.task.Task;
import com.kodality.termx.task.api.TaskStatusChangeInterceptor;
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
