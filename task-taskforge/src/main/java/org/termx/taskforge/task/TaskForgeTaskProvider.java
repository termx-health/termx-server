package org.termx.taskforge.task;

import com.kodality.commons.model.CodeName;
import com.kodality.commons.model.QueryResult;
import org.termx.taskforge.project.Project;
import org.termx.taskforge.project.ProjectService;
import org.termx.taskforge.task.Task;
import org.termx.taskforge.task.TaskService;
import org.termx.taskforge.task.activity.TaskActivity;
import org.termx.taskforge.task.activity.TaskActivity.TaskActivityContextItem;
import org.termx.taskforge.task.activity.TaskActivitySearchParams;
import org.termx.taskforge.task.activity.TaskActivitySearchParams.Ordering;
import org.termx.taskforge.task.activity.TaskActivityService;
import org.termx.taskforge.task.readlog.TaskReadLogService;
import org.termx.taskforge.workflow.WorkflowSearchParams;
import org.termx.taskforge.workflow.WorkflowService;
import com.kodality.termx.task.TaskQueryParams;
import com.kodality.termx.task.api.TaskProvider;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.core.util.StringUtils;
import jakarta.inject.Singleton;
import java.util.List;
import lombok.RequiredArgsConstructor;

@Singleton
@Replaces(TaskProvider.class)
@RequiredArgsConstructor
public class TaskForgeTaskProvider extends TaskProvider {
  private final TaskService taskService;
  private final TaskActivityService taskActivityService;
  private final ProjectService projectService;
  private final WorkflowService workflowService;
  private final TaskReadLogService taskReadLogService;

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

  @Override
  public void logTaskOpened(String taskNumber, String userId) {
    Task task = taskService.load(taskNumber);
    if (task != null) {
      taskReadLogService.logTaskRead(task.getId(), userId);
    }
  }
}
