package org.termx.taskforge.api;

import org.termx.taskforge.task.Task;

public abstract class TaskStatusChangeInterceptor implements TaskInterceptor {

  public abstract void afterStatusChange(Task task, String previousStatus);

  @Override
  public void beforeChange(String event, Task task, Task previous) {}

  @Override
  public void afterChange(String event, Task task, Task previous) {
    if (previous != null && !task.getStatus().equals(previous.getStatus())) {
      afterStatusChange(task, previous.getStatus());
    }
  }
}
