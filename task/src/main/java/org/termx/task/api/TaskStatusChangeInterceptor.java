package org.termx.task.api;


import org.termx.task.Task;

public interface TaskStatusChangeInterceptor {

  void afterStatusChange(Task task, String previousStatus);

}
