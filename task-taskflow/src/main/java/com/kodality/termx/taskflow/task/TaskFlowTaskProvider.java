package com.kodality.termx.taskflow.task;

import com.kodality.commons.model.CodeName;
import com.kodality.commons.model.QueryResult;
import com.kodality.taskflow.project.Project;
import com.kodality.taskflow.project.ProjectService;
import com.kodality.taskflow.task.Task;
import com.kodality.taskflow.task.TaskService;
import com.kodality.taskflow.task.activity.TaskActivity;
import com.kodality.taskflow.task.activity.TaskActivity.TaskActivityContextItem;
import com.kodality.taskflow.task.activity.TaskActivitySearchParams;
import com.kodality.taskflow.task.activity.TaskActivitySearchParams.Ordering;
import com.kodality.taskflow.task.activity.TaskActivityService;
import com.kodality.taskflow.workflow.WorkflowSearchParams;
import com.kodality.taskflow.workflow.WorkflowService;
import com.kodality.termx.task.TaskQueryParams;
import com.kodality.termx.task.api.TaskProvider;
import io.micronaut.core.util.StringUtils;
import jakarta.inject.Singleton;
import java.util.List;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class TaskFlowTaskProvider extends TaskProvider {
  private final TaskService taskService;
  private final TaskActivityService taskActivityService;
  private final ProjectService projectService;
  private final WorkflowService workflowService;

  private final TaskMapper mapper;

  @Override
  public QueryResult<com.kodality.termx.task.Task> queryTasks(TaskQueryParams params) {
    QueryResult<Task> result = taskService.search(mapper.map(params));
    QueryResult<com.kodality.termx.task.Task> termxResult = new QueryResult<>();
    termxResult.setMeta(result.getMeta());
    termxResult.setData(result.getData().stream().map(mapper::map).toList());
    return termxResult;
  }

  @Override
  public com.kodality.termx.task.Task loadTask(String number) {
    Task task = taskService.load(number);
    List<TaskActivity> activities =
        taskActivityService.search(new TaskActivitySearchParams().setTaskIds(task.getId().toString()).setSort(List.of(Ordering.updated)));
    Project project = projectService.load(task.getProjectId());
    return mapper.map(task)
        .setProject(new CodeName().setCode(project.getCode()).setNames(project.getNames()))
        .setActivities(activities.stream().map(mapper::map).toList());
  }

  @Override
  public com.kodality.termx.task.Task saveTask(com.kodality.termx.task.Task termxTask) {
    Task task = mapper.map(termxTask);
    if (task.getNumber() != null) {
      Task existingTask = taskService.load(task.getNumber());
      task.setId(existingTask.getId());
    }
    return mapper.map(taskService.save(task, null));
  }

  @Override
  public void updateStatus(String taskNumber, String newStatus) {
    Task task = taskService.load(taskNumber);
    taskService.updateStatus(task.getId(), newStatus);
  }

  public List<com.kodality.termx.task.Task.TaskActivity> findActivities(String context) {
    if (StringUtils.isEmpty(context)) {
      return List.of();
    }
    TaskActivitySearchParams params = new TaskActivitySearchParams();
    params.setContext(context);
    return taskActivityService.search(params).stream().map(mapper::map).toList();
  }

  @Override
  public com.kodality.termx.task.Task.TaskActivity createTaskActivity(String number, String note,
                                                                      List<com.kodality.termx.task.Task.TaskActivity.TaskActivityContextItem> context) {
    Long taskId = taskService.load(number).getId();
    TaskActivity ta = new TaskActivity();
    ta.setTaskId(taskId);
    ta.setNote(note);
    if (context != null) {
      ta.setContext(context.stream().map(c -> new TaskActivityContextItem().setId(c.getId()).setType(c.getType())).toList());
    }
    return mapper.map(taskActivityService.save(ta));
  }

  @Override
  public com.kodality.termx.task.Task.TaskActivity updateTaskActivity(String number, String activityId, String note) {
    Long taskId = taskService.load(number).getId();
    TaskActivity ta = taskActivityService.load(Long.valueOf(activityId));
    ta.setTaskId(taskId);
    ta.setNote(note);
    return mapper.map(taskActivityService.save(ta));
  }

  @Override
  public void cancelTaskActivity(String number, String activityId) {
    taskActivityService.cancel(Long.valueOf(activityId));
  }

  @Override
  public List<CodeName> loadProjects() {
    return projectService.loadAll().stream().map(p -> new CodeName().setCode(p.getCode()).setNames(p.getNames())).toList();
  }

  @Override
  public List<com.kodality.termx.task.Workflow> loadProjectWorkFlows(String code) {
    WorkflowSearchParams params = new WorkflowSearchParams().all();
    params.setProjectCodes(code);
    return workflowService.search(params).getData().stream().map(mapper::map).toList();
  }
}
