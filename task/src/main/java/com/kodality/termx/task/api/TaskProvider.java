package com.kodality.termx.task.api;

import com.kodality.commons.model.CodeName;
import com.kodality.commons.model.QueryResult;
import com.kodality.termx.task.Task;
import com.kodality.termx.task.Task.TaskActivity;
import com.kodality.termx.task.Task.TaskActivity.TaskActivityContextItem;
import com.kodality.termx.task.TaskQueryParams;
import com.kodality.termx.task.Workflow;
import java.util.List;

public abstract class TaskProvider {
  public abstract QueryResult<Task> queryTasks(TaskQueryParams params);
  public abstract Task loadTask(String number);
  public abstract Task saveTask(Task task);
  public abstract void updateStatus(String taskNumber, String newStatus);
  public abstract List<TaskActivity> findActivities(String context);
  public abstract TaskActivity createTaskActivity(String taskNumber, String note, List<TaskActivityContextItem> context);
  public abstract TaskActivity updateTaskActivity(String taskNumber, String activityId, String note);
  public abstract void cancelTaskActivity(String taskNumber, String activityId);
  public abstract List<CodeName> loadProjects();
  public abstract List<Workflow> loadProjectWorkFlows(String code);
}
