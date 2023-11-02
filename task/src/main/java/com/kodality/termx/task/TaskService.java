package com.kodality.termx.task;

import com.kodality.commons.model.CodeName;
import com.kodality.commons.model.QueryResult;
import com.kodality.termx.task.Task.TaskActivity;
import com.kodality.termx.task.Task.TaskActivity.TaskActivityContextItem;
import com.kodality.termx.task.Task.TaskContextItem;
import com.kodality.termx.task.api.TaskProvider;
import io.micronaut.core.util.StringUtils;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class TaskService {
  private final TaskProvider taskProvider;

  public QueryResult<Task> queryTasks(TaskQueryParams params) {
    return taskProvider.queryTasks(params);
  }

  public Task loadTask(String number) {
    return taskProvider.loadTask(number);
  }

  public Task saveTask(Task task) {
    if (task.getType() == null) {
      task.setType("task");
    }
    return taskProvider.saveTask(task);
  }

  public static String contextString(List<TaskContextItem> contextItems) {
    return contextItems.stream().map(ctx -> ctx.getType() + "|" + ctx.getId()).collect(Collectors.joining(","));
  }

  public static String activityContextString(List<TaskActivityContextItem> contextItems) {
    return contextItems.stream().map(ctx -> ctx.getType() + "|" + ctx.getId()).collect(Collectors.joining(","));
  }

  public void updateStatus(String number, String status) {
    taskProvider.updateStatus(number, status);
  }

  public TaskActivity createTaskActivity(String taskNumber, String note, List<TaskActivityContextItem> context) {
    return taskProvider.createTaskActivity(taskNumber, note, context);
  }

  public void saveTaskActivity(String taskNumber, String note, List<TaskActivityContextItem> context) {
    findActivities(activityContextString(context)).stream().findFirst().ifPresentOrElse(
        a -> updateTaskActivity(taskNumber, a.getId(), note),
        () -> createTaskActivity(taskNumber, note, context)
    );
  }

  public TaskActivity updateTaskActivity(String taskNumber, String activityId, String note) {
    return taskProvider.updateTaskActivity(taskNumber, activityId, note);
  }

  public void cancelTaskActivity(String taskNumber, String activityId) {
    taskProvider.cancelTaskActivity(taskNumber, activityId);
  }

  public List<CodeName> loadProjects() {
    return taskProvider.loadProjects();
  }

  public List<Workflow> loadProjectWorkFlows(String code) {
    return taskProvider.loadProjectWorkFlows(code);
  }

  public void cancelTasks(String context) {
    if (StringUtils.isEmpty(context)) {
      return;
    }
    findTasks(context).forEach(t -> updateStatus(t.getNumber(), TaskStatus.cancelled));
  }
  public void completeTasks(String context) {
    if (StringUtils.isEmpty(context)) {
      return;
    }
    findTasks(context).forEach(t -> updateStatus(t.getNumber(), TaskStatus.completed));
  }

  public List<Task> findTasks(String context) {
    if (StringUtils.isEmpty(context)) {
      return List.of();
    }
    TaskQueryParams params = new TaskQueryParams();
    params.setContext(context);
    params.all();
    return queryTasks(params).getData();
  }

  public List<TaskActivity> findActivities(String context) {
    if (StringUtils.isEmpty(context)) {
      return List.of();
    }
    return taskProvider.findActivities(context);
  }

}
