package com.kodality.termx.taskflow.task;

import com.kodality.taskflow.task.Task;
import com.kodality.taskflow.task.Task.TaskContextItem;
import com.kodality.taskflow.task.TaskSearchParams;
import com.kodality.taskflow.task.activity.TaskActivity;
import com.kodality.taskflow.user.TaskflowUser;
import com.kodality.taskflow.workflow.Workflow;
import com.kodality.termx.task.TaskQueryParams;
import com.kodality.termx.task.Workflow.WorkflowTransition;
import javax.inject.Singleton;

@Singleton
public class TaskMapper {

  public Task map(com.kodality.termx.task.Task termxTask) {
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

  public com.kodality.termx.task.Task map(Task task) {
    com.kodality.termx.task.Task termxTask = new com.kodality.termx.task.Task();
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
          .map(c -> new com.kodality.termx.task.Task.TaskContextItem().setId(c.getId()).setType(c.getType())).toList());
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

  public com.kodality.termx.task.Task.TaskActivity map(TaskActivity activity) {
    com.kodality.termx.task.Task.TaskActivity termxActivity = new com.kodality.termx.task.Task.TaskActivity();
    termxActivity.setNote(activity.getNote());
    termxActivity.setUpdatedBy(activity.getUpdatedBy() == null ? null : activity.getUpdatedBy().getSub());
    termxActivity.setUpdatedAt(activity.getUpdatedAt());
    return termxActivity;
  }

  public com.kodality.termx.task.Workflow map(Workflow workflow) {
    com.kodality.termx.task.Workflow termxWorkflow = new com.kodality.termx.task.Workflow();
    termxWorkflow.setCode(workflow.getTaskType());
    termxWorkflow.setTransitions(workflow.getTransitions().stream().map(t -> new WorkflowTransition().setFrom(t.getFrom()).setTo(t.getTo())).toList());
    return termxWorkflow;
  }
}
