package com.kodality.termserver.taskflow;

import com.kodality.commons.model.CodeName;
import com.kodality.commons.model.LocalizedName;
import com.kodality.taskflow.space.Space;
import com.kodality.taskflow.space.SpaceService;
import com.kodality.taskflow.task.Task;
import com.kodality.taskflow.task.Task.TaskPriority;
import com.kodality.taskflow.task.Task.TaskStatus;
import com.kodality.taskflow.task.TaskSearchParams;
import com.kodality.taskflow.task.TaskService;
import com.kodality.taskflow.workflow.Workflow;
import com.kodality.taskflow.workflow.Workflow.WorkflowTransition;
import com.kodality.taskflow.workflow.WorkflowSearchParams;
import com.kodality.taskflow.workflow.WorkflowService;
import com.kodality.termserver.ts.Language;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class CommonTaskService {
  private final TaskService taskService;
  private final SpaceService spaceService;
  private final WorkflowService workflowService;

  private final static String SPACE_CODE = "kts";
  private final static String INSTITUTION = "1";
  private final static String TASK_TYPE = "task";

  public Map<String, List<CodeName>> findTaskCtxGroup(String context) {
    List<Task> tasks = findTasks(context);
    return tasks.stream()
        .filter(t -> t.getContext() != null)
        .collect(Collectors.groupingBy(t -> t.getContext().stream().map(c -> c.getType() + "|" + c.getId()).collect(Collectors.joining(","))))
        .entrySet().stream()
        .map(es -> Pair.of(es.getKey(), es.getValue().stream()
            .map(t -> new CodeName().setId(t.getId()).setCode(t.getNumber()).setNames(new LocalizedName(Map.of(Language.en, t.getTitle())))).toList()))
        .collect(Collectors.toMap(Pair::getKey, Pair::getValue));
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

  @Transactional
  public void createTask(Task task, String workflow) {
    String context = task.getContext().stream().map(ctx -> ctx.getType() + "|" + ctx.getId()).collect(Collectors.joining(","));
    Optional<Task> exitingTask = findTasks(context).stream().findFirst();
    task.setId(exitingTask.map(Task::getId).orElse(null));
    task.setSpaceId(getSpaceId());
    task.setWorkflowId(getWorkflowId(workflow, task.getSpaceId()));
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


  private Long getSpaceId() {
    Space space = spaceService.load(SPACE_CODE, INSTITUTION);
    if (space == null) {
      return createSpace();
    }
    return space.getId();

  }

  private Long createSpace() {
    Space space = new Space();
    space.setCode(SPACE_CODE);
    space.setNames(new LocalizedName(Map.of(Language.en, "Kodality Terminology Service")));
    space.setInstitution(INSTITUTION);
    return spaceService.save(space).getId();
  }

  private Long getWorkflowId(String type, Long spaceId) {
    WorkflowSearchParams params = new WorkflowSearchParams();
    params.setTypes(type);
    params.setSpaceIds(String.valueOf(spaceId));
    params.setLimit(1);
    Workflow workflow = workflowService.search(params).findFirst().orElse(null);
    if (workflow == null) {
      return createWorkflow(type, spaceId);
    }
    return workflow.getId();
  }

  private Long createWorkflow(String type, Long spaceId) {
    Workflow workflow = new Workflow();
    workflow.setTaskType(type);
    workflow.setTransitions(List.of(
        new WorkflowTransition().setFrom(null).setTo(TaskStatus.draft),
        new WorkflowTransition().setFrom(null).setTo(TaskStatus.requested),

        new WorkflowTransition().setFrom(TaskStatus.draft).setTo(TaskStatus.requested),
        new WorkflowTransition().setFrom(TaskStatus.draft).setTo(TaskStatus.cancelled),

        new WorkflowTransition().setFrom(TaskStatus.requested).setTo(TaskStatus.received),
        new WorkflowTransition().setFrom(TaskStatus.requested).setTo(TaskStatus.accepted),
        new WorkflowTransition().setFrom(TaskStatus.requested).setTo(TaskStatus.rejected),
        new WorkflowTransition().setFrom(TaskStatus.requested).setTo(TaskStatus.failed),
        new WorkflowTransition().setFrom(TaskStatus.requested).setTo(TaskStatus.cancelled),

        new WorkflowTransition().setFrom(TaskStatus.received).setTo(TaskStatus.accepted),
        new WorkflowTransition().setFrom(TaskStatus.received).setTo(TaskStatus.rejected),
        new WorkflowTransition().setFrom(TaskStatus.received).setTo(TaskStatus.failed),
        new WorkflowTransition().setFrom(TaskStatus.received).setTo(TaskStatus.cancelled),

        new WorkflowTransition().setFrom(TaskStatus.rejected).setTo(TaskStatus.draft),
        new WorkflowTransition().setFrom(TaskStatus.rejected).setTo(TaskStatus.received),
        new WorkflowTransition().setFrom(TaskStatus.rejected).setTo(TaskStatus.cancelled),

        new WorkflowTransition().setFrom(TaskStatus.failed).setTo(TaskStatus.draft),
        new WorkflowTransition().setFrom(TaskStatus.failed).setTo(TaskStatus.in_progress),
        new WorkflowTransition().setFrom(TaskStatus.failed).setTo(TaskStatus.cancelled),

        new WorkflowTransition().setFrom(TaskStatus.accepted).setTo(TaskStatus.failed),
        new WorkflowTransition().setFrom(TaskStatus.accepted).setTo(TaskStatus.cancelled),

        new WorkflowTransition().setFrom(TaskStatus.in_progress).setTo(TaskStatus.requested),
        new WorkflowTransition().setFrom(TaskStatus.in_progress).setTo(TaskStatus.cancelled),

        new WorkflowTransition().setFrom(TaskStatus.cancelled).setTo(TaskStatus.draft)
    ));
    workflowService.save(spaceId, List.of(workflow));
    return workflow.getId();
  }

  public void cancelTasks(List<Long> ids, String taskCtxType) {
    if (CollectionUtils.isEmpty(ids)) {
      return;
    }
    List<Task> tasks = findTasks(ids.stream().map(id -> taskCtxType + "|" + id).collect(Collectors.joining(",")));
    tasks.forEach(t -> {
      t.setStatus(TaskStatus.cancelled);
      taskService.save(t, null);
    });
  }
}
