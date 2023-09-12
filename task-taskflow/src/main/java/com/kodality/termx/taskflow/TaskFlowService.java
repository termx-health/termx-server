package com.kodality.termx.taskflow;

import com.kodality.taskflow.project.ProjectService;
import com.kodality.taskflow.task.Task;
import com.kodality.taskflow.task.Task.TaskContextItem;
import com.kodality.taskflow.task.Task.TaskPriority;
import com.kodality.taskflow.task.Task.TaskStatus;
import com.kodality.taskflow.task.TaskSearchParams;
import com.kodality.taskflow.task.TaskService;
import com.kodality.taskflow.task.activity.TaskActivity;
import com.kodality.taskflow.task.activity.TaskActivity.TaskActivityContextItem;
import com.kodality.taskflow.task.activity.TaskActivitySearchParams;
import com.kodality.taskflow.task.activity.TaskActivityService;
import com.kodality.taskflow.workflow.WorkflowSearchParams;
import com.kodality.taskflow.workflow.WorkflowService;
import io.micronaut.core.util.StringUtils;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class TaskFlowService {
  private final TaskService taskService;
  private final TaskActivityService taskActivityService;
  private final ProjectService projectService;
  private final WorkflowService workflowService;

  private final static String PROJECT_CODE = "termx";
  private final static String INSTITUTION = "1";
  private final static String TASK_TYPE = "task";


  private Long getProjectId() {
    return projectService.load(PROJECT_CODE, INSTITUTION).getId();
  }

  private Long getWorkflowId(String type, Long projectId) {
    WorkflowSearchParams params = new WorkflowSearchParams();
    params.setTypes(type);
    params.setProjectIds(String.valueOf(projectId));
    params.setLimit(1);
    return workflowService.search(params).findFirst().orElseThrow().getId();
  }


  public List<Task> findTasks(String context) {
    if (StringUtils.isEmpty(context)) {
      return List.of();
    }
    TaskSearchParams params = new TaskSearchParams();
    params.setContext(context);
    params.all();
    return taskService.search(params).getData();
  }

  public void createTask(Task task, String workflow) {
    String context = contextString(task.getContext());
    Optional<Task> exitingTask = findTasks(context).stream().findFirst();
    task.setId(exitingTask.map(Task::getId).orElse(null));
    task.setProjectId(getProjectId());
    task.setWorkflowId(getWorkflowId(workflow, task.getProjectId()));
    task.setParentId(exitingTask.map(Task::getParentId).orElse(null));
    task.setNumber(exitingTask.map(Task::getNumber).orElse(null));
    task.setType(task.getType() == null ? TASK_TYPE : task.getType());
    task.setStatus(task.getStatus() == null ? exitingTask.map(Task::getStatus).orElse(TaskStatus.requested) : task.getStatus());
    task.setPriority(task.getPriority() == null ? exitingTask.map(Task::getPriority).orElse(TaskPriority.routine) : task.getPriority());
    task.setCreatedBy(exitingTask.map(Task::getCreatedBy).orElse(null));
    task.setCreatedAt(exitingTask.map(Task::getCreatedAt).orElse(null));
    task.setAssignee(exitingTask.map(Task::getAssignee).orElse(null));
    task.setUpdatedBy(exitingTask.map(Task::getUpdatedBy).orElse(null));
    task.setUpdatedAt(exitingTask.map(Task::getUpdatedAt).orElse(null));
    taskService.save(task, null);
  }

  public void cancelTasks(String context) {
    updateStatus(context, TaskStatus.cancelled);
  }

  public void completeTasks(String context) {
    updateStatus(context, TaskStatus.completed);
  }

  private void updateStatus(String context, String newStatus) {
    if (StringUtils.isEmpty(context)) {
      return;
    }
    List<Task> tasks = findTasks(context);
    tasks.forEach(t -> {
      taskService.updateStatus(t.getId(), newStatus);
    });
  }


  public List<TaskActivity> findActivities(String context) {
    if (StringUtils.isEmpty(context)) {
      return List.of();
    }
    TaskActivitySearchParams params = new TaskActivitySearchParams();
    params.setContext(context);
    return taskActivityService.search(params);
  }

  public void createTaskActivity(TaskActivity ta) {
    String context = ta.getContext() == null ? null : activityContextString(ta.getContext());
    Optional<TaskActivity> existingActivity = findActivities(context).stream().findFirst();
    ta.setTaskId(existingActivity.map(TaskActivity::getTaskId).orElse(ta.getTaskId()));
    ta.setId(existingActivity.map(TaskActivity::getId).orElse(null));
    ta.setUpdatedBy(existingActivity.map(TaskActivity::getUpdatedBy).orElse(null));
    ta.setUpdatedAt(existingActivity.map(TaskActivity::getUpdatedAt).orElse(null));
    taskActivityService.save(ta);
  }

  public void cancelTaskActivity(Long taskActivityId) {
    taskActivityService.cancel(taskActivityId);
  }


  public static String contextString(List<TaskContextItem> contextItems) {
    return contextItems.stream().map(ctx -> ctx.getType() + "|" + ctx.getId()).collect(Collectors.joining(","));
  }

  public static String activityContextString(List<TaskActivityContextItem> contextItems) {
    return contextItems.stream().map(ctx -> ctx.getType() + "|" + ctx.getId()).collect(Collectors.joining(","));
  }
}
