package com.kodality.termx.task;

import com.kodality.commons.model.CodeName;
import com.kodality.commons.model.QueryResult;
import com.kodality.termx.Privilege;
import com.kodality.termx.auth.Authorized;
import com.kodality.termx.task.Task.TaskActivity;
import com.kodality.termx.utils.PatchUtil;
import com.kodality.termx.utils.PatchUtil.PatchRequest;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Patch;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.validation.Validated;
import java.util.List;
import java.util.Map;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;

@Validated
@Controller("/tm")
@RequiredArgsConstructor
public class TaskController {
  private final TaskProvider taskProvider;

  // ----------- Tasks -----------

  @Authorized(privilege = Privilege.T_VIEW)
  @Get(uri = "/tasks{?params*}")
  public QueryResult<Task> queryTasks(TaskQueryParams params) {
    return taskProvider.queryTasks(params);
  }

  @Authorized(privilege = Privilege.T_VIEW)
  @Get(uri = "/tasks/{number}")
  public Task loadTask(@Parameter String number) {
    return taskProvider.loadTask(number);
  }

  @Authorized(privilege = Privilege.T_EDIT)
  @Post("/tasks")
  public Task saveTask(@Body Task task) {
    task.setNumber(null);
    return taskProvider.saveTask(task);
  }

  @Authorized(privilege = Privilege.T_EDIT)
  @Put("/tasks/{number}")
  public Task updateTask(@Parameter String number, @Body Task task) {
    task.setNumber(number);
    return taskProvider.saveTask(task);
  }

  @Authorized(privilege = Privilege.T_EDIT)
  @Patch("/tasks/{number}")
  public Task patchTask(@Parameter String number, @Body PatchRequest request) {
    Task currentTask = taskProvider.loadTask(number);
    return taskProvider.saveTask(PatchUtil.mergeFields(request, currentTask));
  }

  @Authorized(privilege = Privilege.T_EDIT)
  @Post("/tasks/{number}/activities")
  public TaskActivity createTaskActivity(@Parameter String number, @Valid @Body Map<String, String> body) {
    return taskProvider.createTaskActivity(number, body.get("note"));
  }

  @Authorized(privilege = Privilege.T_EDIT)
  @Put("/tasks/{number}/activities/{id}")
  public TaskActivity createTaskActivity(@Parameter String number, @Parameter String id, @Valid @Body Map<String, String> body) {
    return taskProvider.updateTaskActivity(number, id, body.get("note"));
  }

  @Authorized(privilege = Privilege.T_EDIT)
  @Delete("/tasks/{number}/activities/{id}")
  public HttpResponse<?> deleteTaskActivity(@Parameter String number, @Parameter String id) {
    taskProvider.cancelTaskActivity(number, id);
    return HttpResponse.ok();
  }


  // ----------- Projects -----------

  @Authorized(privilege = Privilege.T_VIEW)
  @Get(uri = "/projects")
  public List<CodeName> loadProjects() {
    return taskProvider.loadProjects();
  }

  @Authorized(privilege = Privilege.T_VIEW)
  @Get(uri = "/projects/{code}/workflows")
  public List<Workflow> loadProjectWorkflows(@Parameter String code) {
    return taskProvider.loadProjectWorkFlows(code);
  }
}
