package com.kodality.termx.task.api;


import com.kodality.termx.task.Task;

public interface TaskStatusChangeInterceptor {

  void afterStatusChange(Task task, String previousStatus);

}
