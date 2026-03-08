package org.termx.taskforge.task;

import org.termx.core.acl.AclAccess;
import org.termx.core.acl.AclService;
import com.kodality.commons.exception.NotFoundException;
import com.kodality.commons.micronaut.rest.MultipartBodyReader.Attachment;
import com.kodality.commons.model.QueryResult;
import org.termx.core.sequence.SequenceService;
import com.kodality.commons.util.JsonUtil;
import org.termx.taskforge.ApiError;
import org.termx.taskforge.api.TaskInterceptor;
import org.termx.taskforge.auth.TaskforgeSessionProvider;
import org.termx.taskforge.project.ProjectService;
import org.termx.taskforge.task.attachment.TaskAttachmentService;
import org.termx.taskforge.user.TaskforgeUser;
import jakarta.inject.Singleton;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class TaskService {
  private final TaskRepository taskRepository;
  private final TaskAttachmentService taskAttachmentService;
  private final ProjectService projectService;
  private final TaskforgeSessionProvider session;
  private final AclService aclService;
  private final SequenceService sequenceService;
  private final List<TaskInterceptor> interceptors;

  public void validateAccess(Long taskId) {
    Task task = taskRepository.load(taskId, session.requireTenant());
    if (task == null) {
      throw new NotFoundException("task", taskId);
    }
    aclService.validate(task.getProjectId(), session.requireTenant(), AclAccess.view);
  }

  public Task save(Task task, Map<String, Attachment> newAttachments) {
    aclService.validate(task.getProjectId(), session.requireTenant(), AclAccess.consume);

    Task current = task.getId() == null ? null : load(task.getId());
    interceptors.forEach(i -> i.beforeChange(TaskInterceptor.SAVE, task, current));

    if (current == null) {
      task.setNumber(sequenceService.getNextValue("task", null, LocalDate.now(), session.requireTenant()));
      task.setCreatedBy(new TaskforgeUser().setSub(session.require().getSub()));
      task.setCreatedAt(OffsetDateTime.now());
      projectService.validateTransition(task.getWorkflowId(), null, task.getStatus());
    } else {
      if (!current.getProjectId().equals(task.getProjectId())) {
        throw ApiError.TF100.toApiException();
      }
      projectService.validateTransition(task.getWorkflowId(), current.getStatus(), task.getStatus());
    }
    task.setUpdatedAt(OffsetDateTime.now());
    task.setUpdatedBy(new TaskforgeUser().setSub(session.require().getSub()));
    Long id = taskRepository.save(task);
    aclService.init(id, session.requireTenant());
    task.setAttachments(taskAttachmentService.save(id, task.getAttachments(), newAttachments));

    Task resultTask = load(id);
    interceptors.forEach(i -> i.afterChange(TaskInterceptor.SAVE, resultTask, current));
    return resultTask;
  }

  public void updateStatus(Long taskId, String newStatus) {
    aclService.validate(taskId, session.requireTenant(), AclAccess.consume);
    Task current = load(taskId);
    if (current == null) {
      throw new NotFoundException("task", taskId);
    }
    if (newStatus.equals(current.getStatus())) {
      return;
    }
    projectService.validateTransition(current.getWorkflowId(), current.getStatus(), newStatus);

    Task task = JsonUtil.fromJson(JsonUtil.toJson(current), Task.class);
    task.setStatus(newStatus);
    interceptors.forEach(i -> i.beforeChange(TaskInterceptor.UPDATE_STATUS, task, current));
    taskRepository.updateStatus(taskId, newStatus);
    interceptors.forEach(i -> i.afterChange(TaskInterceptor.UPDATE_STATUS, task, current));
  }

  public Task load(Long id) {
    Task task = taskRepository.load(id, session.requireTenant());
    if (task != null) {
      task.setAttachments(taskAttachmentService.loadAll(task.getId()));
    }
    return task;
  }

  public Task load(String number) {
    Task task = taskRepository.load(number, session.requireTenant());
    if (task != null) {
      task.setAttachments(taskAttachmentService.loadAll(task.getId()));
    }
    return task;
  }

  public QueryResult<Task> search(TaskSearchParams params) {
    return taskRepository.search(params, session.requireTenant());
  }
}
