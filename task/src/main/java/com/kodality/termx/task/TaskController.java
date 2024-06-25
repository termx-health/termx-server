package com.kodality.termx.task;

import com.kodality.commons.model.CodeName;
import com.kodality.commons.model.QueryResult;
import com.kodality.termx.core.auth.Authorized;
import com.kodality.termx.core.utils.PatchUtil;
import com.kodality.termx.core.utils.PatchUtil.PatchRequest;
import com.kodality.termx.task.Task.TaskActivity;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Patch;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.validation.Validated;
import java.util.List;
import java.util.Map;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Validated
@Controller("/tm")
@RequiredArgsConstructor
public class TaskController {
  private final TaskService taskService;

  // ----------- Tasks -----------

  @Authorized(privilege = Privilege.T_VIEW)
  @Get(uri = "/tasks{?params*}")
  public QueryResult<Task> queryTasks(TaskQueryParams params) {
    return taskService.queryTasks(params);
  }

  @Authorized(privilege = Privilege.T_VIEW)
  @Get(uri = "/tasks/{number}")
  public Task loadTask(@PathVariable String number) {
    return taskService.loadTask(number);
  }

  @Authorized(privilege = Privilege.T_EDIT)
  @Post("/tasks")
  public Task saveTask(@Body Task task) {
    task.setNumber(null);
    return taskService.saveTask(task);
  }

  @Authorized(privilege = Privilege.T_EDIT)
  @Put("/tasks/{number}")
  public Task updateTask(@PathVariable String number, @Body Task task) {
    task.setNumber(number);
    return taskService.saveTask(task);
  }

  @Authorized(privilege = Privilege.T_EDIT)
  @Patch("/tasks/{number}")
  public Task patchTask(@PathVariable String number, @Body PatchRequest request) {
    Task currentTask = taskService.loadTask(number);
    return taskService.saveTask(PatchUtil.mergeFields(request, currentTask));
  }

  @Authorized(privilege = Privilege.T_EDIT)
  @Post("/tasks/{number}/activities")
  public TaskActivity createTaskActivity(@PathVariable String number, @Valid @Body Map<String, String> body) {
    return taskService.createTaskActivity(number, body.get("note"), null);
  }

  @Authorized(privilege = Privilege.T_EDIT)
  @Put("/tasks/{number}/activities/{id}")
  public TaskActivity createTaskActivity(@PathVariable String number, @PathVariable String id, @Valid @Body Map<String, String> body) {
    return taskService.updateTaskActivity(number, id, body.get("note"));
  }

  @Authorized(privilege = Privilege.T_EDIT)
  @Delete("/tasks/{number}/activities/{id}")
  public HttpResponse<?> deleteTaskActivity(@PathVariable String number, @PathVariable String id) {
    taskService.cancelTaskActivity(number, id);
    return HttpResponse.ok();
  }


  // ----------- Projects -----------

  @Authorized(privilege = Privilege.T_VIEW)
  @Get(uri = "/projects")
  public List<CodeName> loadProjects() {
    return taskService.loadProjects();
  }

  @Authorized(privilege = Privilege.T_VIEW)
  @Get(uri = "/projects/{code}/workflows")
  public List<Workflow> loadProjectWorkflows(@PathVariable String code) {
    return taskService.loadProjectWorkFlows(code);
  }
}
