package com.kodality.termx.taskflow.task;

import com.kodality.commons.stream.Collectors;
import com.kodality.taskflow.task.Task;
import com.kodality.taskflow.task.Task.TaskContextItem;
import com.kodality.taskflow.task.TaskSearchParams;
import com.kodality.taskflow.task.activity.TaskActivity;
import com.kodality.taskflow.user.TaskflowUser;
import com.kodality.taskflow.workflow.Workflow;
import com.kodality.termx.task.task.Task.TaskActivity.TaskActivityContextItem;
import com.kodality.termx.task.task.TaskQueryParams;
import com.kodality.termx.task.task.Workflow.WorkflowTransition;
import jakarta.inject.Singleton;
import java.util.Map.Entry;

@Singleton
public class TaskMapper {

  public Task map(com.kodality.termx.task.task.Task termxTask) {
    Task task = new Task();
    task.setNumber(termxTask.getNumber());
    task.setType(termxTask.getType());
    task.setStatus(termxTask.getStatus());
    task.setPriority(termxTask.getPriority());
    task.setCreatedBy(new TaskflowUser().setSub(termxTask.getCreatedBy()));
    task.setCreatedAt(termxTask.getCreatedAt());
    task.setAssignee(new TaskflowUser().setSub(termxTask.getAssignee()));
    task.setUpdatedBy(new TaskflowUser().setSub(termxTask.getUpdatedBy()));
    task.setUpdatedAt(termxTask.getUpdatedAt());
    task.setTitle(termxTask.getTitle());
    task.setContent(termxTask.getContent());
    if (termxTask.getContext() != null) {
      task.setContext(termxTask.getContext().stream()
          .map(c -> new TaskContextItem().setId(c.getId()).setType(c.getType())).toList());
    }
    return task;
  }

  public com.kodality.termx.task.task.Task map(Task task) {
    com.kodality.termx.task.task.Task termxTask = new com.kodality.termx.task.task.Task();
    termxTask.setNumber(task.getNumber());
    termxTask.setType(task.getType());
    termxTask.setStatus(task.getStatus());
    termxTask.setPriority(task.getPriority());
    termxTask.setCreatedBy(task.getCreatedBy() == null ? null : task.getCreatedBy().getSub());
    termxTask.setCreatedAt(task.getCreatedAt());
    termxTask.setAssignee(task.getAssignee() == null ? null : task.getAssignee().getSub());
    termxTask.setUpdatedBy(task.getUpdatedBy() == null ? null : task.getUpdatedBy().getSub());
    termxTask.setUpdatedAt(task.getUpdatedAt());
    termxTask.setTitle(task.getTitle());
    termxTask.setContent(task.getContent());
    if (task.getContext() != null) {
      termxTask.setContext(task.getContext().stream()
          .map(c -> new com.kodality.termx.task.task.Task.TaskContextItem().setId(c.getId()).setType(c.getType())).toList());
    }
    return termxTask;
  }

  public TaskSearchParams map(TaskQueryParams params) {
    TaskSearchParams searchParams = new TaskSearchParams();
    searchParams.setStatuses(params.getStatuses());
    searchParams.setStatusesNe(params.getStatusesNe());
    searchParams.setPriorities(params.getPriorities());
    searchParams.setTextContains(params.getTextContains());
    searchParams.setCreatedGe(params.getCreatedGe());
    searchParams.setCreatedLe(params.getCreatedLe());
    searchParams.setCreatedBy(params.getCreatedBy());
    searchParams.setModifiedGe(params.getModifiedGe());
    searchParams.setModifiedLe(params.getModifiedLe());
    searchParams.setAssignees(params.getAssignees());
    searchParams.setContext(params.getContext());
    searchParams.setTypes(params.getTypes());
    searchParams.setContext(params.getContext());

    searchParams.setLimit(params.getLimit());
    searchParams.setOffset(params.getOffset());
    searchParams.setSort(params.getSort());
    return searchParams;
  }

  public com.kodality.termx.task.task.Task.TaskActivity map(TaskActivity activity) {
    com.kodality.termx.task.task.Task.TaskActivity termxActivity = new com.kodality.termx.task.task.Task.TaskActivity();
    termxActivity.setId(activity.getId().toString());
    termxActivity.setNote(activity.getNote());
    if (activity.getTransition() != null) {
      termxActivity.setTransition(activity.getTransition().entrySet().stream().collect(Collectors.toMap(Entry::getKey, o -> {
        var tat = new com.kodality.termx.task.task.Task.TaskActivity.TaskActivityTransition();
        tat.setFrom(o.getValue().getFrom());
        tat.setTo(o.getValue().getTo());
        return tat;
      })));
    }
    if (activity.getContext() != null) {
      termxActivity.setContext(activity.getContext().stream().map(c -> new TaskActivityContextItem().setId(c.getId()).setType(c.getType())).toList());
    }
    termxActivity.setUpdatedBy(activity.getUpdatedBy() == null ? null : activity.getUpdatedBy().getSub());
    termxActivity.setUpdatedAt(activity.getUpdatedAt());
    return termxActivity;
  }

  public com.kodality.termx.task.task.Workflow map(Workflow workflow) {
    com.kodality.termx.task.task.Workflow termxWorkflow = new com.kodality.termx.task.task.Workflow();
    termxWorkflow.setCode(workflow.getTaskType());
    termxWorkflow.setTransitions(workflow.getTransitions().stream().map(t -> new WorkflowTransition().setFrom(t.getFrom()).setTo(t.getTo())).toList());
    return termxWorkflow;
  }
}
