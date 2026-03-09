package org.termx.task;

import com.kodality.commons.model.CodeName;
import com.kodality.commons.model.QueryResult;
import com.kodality.termx.core.auth.Authorized;
import com.kodality.termx.core.auth.SessionInfo;
import com.kodality.termx.core.auth.SessionStore;
import com.kodality.termx.core.utils.PatchUtil;
import com.kodality.termx.core.utils.PatchUtil.PatchRequest;
import org.termx.task.Task.TaskActivity;
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
import java.util.ArrayList;
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
    SessionInfo session = SessionStore.require();

    if (Boolean.TRUE.equals(params.getUnseenChanges())) {
      params.setUnseenChangesUser(session.getUsername());
    }

    if (!isAdmin(session)) {
      TaskQueryParams.TaskVisibilityFilter filter = new TaskQueryParams.TaskVisibilityFilter();
      filter.setUsername(session.getUsername());
      filter.setPublisherContexts(getPermittedContexts(session, "publish"));
      params.setVisibilityFilter(filter);
    }
    return taskService.queryTasks(params);
  }

  @Authorized(privilege = Privilege.T_VIEW)
  @Get(uri = "/tasks/{number}")
  public Task loadTask(@PathVariable String number) {
    SessionInfo session = SessionStore.require();

    Task task = taskService.loadTask(number);
    if (task == null) {
      return null;
    }
    if (isAdmin(session)) {
      return task;
    }
    if (isOwnTask(task, session) || isContextPermitted(task, session, "publish")) {
      return task;
    }
    return null;
  }

  private boolean isAdmin(SessionInfo session) {
    return session.hasPrivilege("*.*.*");
  }

  private boolean isOwnTask(Task task, SessionInfo session) {
    String username = session.getUsername();
    return username.equals(task.getCreatedBy()) || username.equals(task.getAssignee());
  }

  private static final List<String> CONTEXT_TYPES = List.of("code-system", "value-set", "map-set");

  /**
   * Returns null if the user has access to all resources (wildcard); otherwise returns the list of
   * type|id pairs the user has access to. Used for SQL-level filtering.
   */
  private List<String> getPermittedContexts(SessionInfo session, String action) {
    List<String> result = new ArrayList<>();
    boolean allPermitted = true;
    
    for (String contextType : CONTEXT_TYPES) {
      // Check for privileges like code-system#icd-10.Task.publish
      List<String> ids = getPermittedResourceIds(session, contextType, action);
      if (ids == null) {
        continue; // null = all resources of this type (code-system#*.Task.publish)
      }
      allPermitted = false;
      for (String id : ids) {
        result.add(contextType + "|" + id);
      }
    }
    return allPermitted ? null : result;
  }

  private List<String> getPermittedResourceIds(SessionInfo session, String contextType, String action) {
    if (session.getPrivileges() == null) {
      return List.of();
    }
    
    // Check for wildcard: code-system#*.Task.publish
    String wildcardPrivilege = contextType + "#*.Task." + action;
    if (session.hasPrivilege(wildcardPrivilege)) {
      return null; // null = all resources
    }
    
    // Extract resource IDs from privileges like code-system#icd-10.Task.publish
    String suffix = ".Task." + action;
    String prefix = contextType + "#";
    
    return session.getPrivileges().stream()
        .filter(p -> p.startsWith(prefix) && p.endsWith(suffix))
        .map(p -> p.substring(prefix.length(), p.length() - suffix.length()))
        .toList();
  }

  private boolean isContextPermitted(Task task, SessionInfo session, String action) {
    if (task.getContext() == null || task.getContext().isEmpty()) {
      return true;
    }
    return task.getContext().stream().allMatch(ctx -> {
      // Build privilege string using context type directly: code-system#icd-10.Task.publish
      String privilege = ctx.getType() + "#" + ctx.getId() + ".Task." + action;
      return session.hasPrivilege(privilege);
    });
  }

  @Authorized(privilege = Privilege.T_VIEW)
  @Post("/tasks/{number}/opened")
  public HttpResponse<?> logTaskOpened(@PathVariable String number) {
    SessionInfo session = SessionStore.require();
    taskService.logTaskOpened(number, session.getUsername());
    return HttpResponse.ok();
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
