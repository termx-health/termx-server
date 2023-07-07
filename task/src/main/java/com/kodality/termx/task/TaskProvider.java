package com.kodality.termx.task;

import com.kodality.commons.model.CodeName;
import com.kodality.commons.model.QueryResult;
import com.kodality.termx.task.Task.TaskActivity;
import java.util.List;

public abstract class TaskProvider {
  public abstract QueryResult<Task> queryTasks(TaskQueryParams params);
  public abstract Task loadTask(String number);
  public abstract Task saveTask(Task task);
  public abstract TaskActivity createTaskActivity(String number, String note);
  public abstract List<CodeName> loadProjects();
  public abstract List<Workflow> loadProjectWorkFlows(String code);
}
