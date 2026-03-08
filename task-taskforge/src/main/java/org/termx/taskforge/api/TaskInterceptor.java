package org.termx.taskforge.api;

import org.termx.taskforge.task.Task;

public interface TaskInterceptor {
  String SAVE = "save";
  String UPDATE_STATUS = "update-status";

  default void beforeChange(String event, Task task, Task previous) {}

  default void afterChange(String event, Task sr, Task previous) {}
}
