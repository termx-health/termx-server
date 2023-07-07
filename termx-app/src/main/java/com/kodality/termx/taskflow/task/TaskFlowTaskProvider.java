package com.kodality.termx.taskflow.task;

import com.kodality.commons.model.CodeName;
import com.kodality.commons.model.QueryResult;
import com.kodality.taskflow.project.Project;
import com.kodality.taskflow.project.ProjectService;
import com.kodality.taskflow.task.Task;
import com.kodality.taskflow.task.TaskService;
import com.kodality.taskflow.task.activity.TaskActivity;
import com.kodality.taskflow.task.activity.TaskActivityService;
import com.kodality.taskflow.workflow.Workflow;
import com.kodality.taskflow.workflow.WorkflowSearchParams;
import com.kodality.taskflow.workflow.WorkflowService;
import com.kodality.termx.task.TaskProvider;
import com.kodality.termx.task.TaskQueryParams;
import java.util.List;
import javax.inject.Singleton;
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
    List<TaskActivity> activities = taskActivityService.loadAll(task.getId());
    Project project = projectService.load(task.getProjectId());
    Workflow workflow = workflowService.load(task.getWorkflowId());
    return mapper.map(task)
        .setProject(new CodeName().setCode(project.getCode()).setNames(project.getNames()))
        .setWorkflow(workflow.getTaskType())
        .setActivities(activities.stream().map(mapper::map).toList());
  }

  @Override
  public com.kodality.termx.task.Task saveTask(com.kodality.termx.task.Task termxTask) {
    Task task = mapper.map(termxTask);
    if (task.getNumber() != null) {
      Task existingTask = taskService.load(task.getNumber());
      task.setId(existingTask.getId());
      task.setProjectId(existingTask.getProjectId());
    } else {
      task.setProjectId(projectService.load(termxTask.getProject().getCode(), "1").getId());
    }
    task.setWorkflowId(workflowService.search(new WorkflowSearchParams().setTypes(termxTask.getWorkflow())).findFirst().map(Workflow::getId).orElse(null));
    return mapper.map(taskService.save(task, null));
  }

  @Override
  public com.kodality.termx.task.Task.TaskActivity createTaskActivity(String number, String note) {
    Long taskId = taskService.load(number).getId();
    return mapper.map(taskActivityService.create(taskId, note));
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
