package com.kodality.termx.wiki;

import com.kodality.taskflow.api.TaskStatusChangeInterceptor;
import com.kodality.taskflow.task.Task;
import com.kodality.taskflow.workflow.Workflow;
import com.kodality.taskflow.workflow.WorkflowService;
import com.kodality.termx.wiki.page.PageComment;
import com.kodality.termx.wiki.page.PageCommentStatus;
import com.kodality.termx.wiki.pagecomment.PageCommentService;
import javax.inject.Provider;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;

import static com.kodality.taskflow.task.Task.TaskStatus.cancelled;
import static com.kodality.taskflow.task.Task.TaskStatus.completed;


@Singleton
@RequiredArgsConstructor
public class WikiPageCommentTaskFlowStatusChangeInterceptor extends TaskStatusChangeInterceptor {
  private final WorkflowService workflowService;
  private final Provider<PageCommentService> commentService;

  public static final String TASK_CTX_TYPE = "page-comment";
  public static final String TASK_WORKFLOW = "wiki-page-comment";

  @Override
  public void afterStatusChange(Task task, String previousStatus) {
    if (task == null || task.getWorkflowId() == null || task.getContext() == null) {
      return;
    }
    Workflow workflow = workflowService.load(task.getWorkflowId());
    if (!TASK_WORKFLOW.equals(workflow.getTaskType())) {
      return;
    }
    Long commentId = task.getContext().stream().filter(ctx -> TASK_CTX_TYPE.equals(ctx.getType())).findFirst().map(t -> (Long) t.getId()).orElseThrow();
    PageComment comment = commentService.get().load(commentId);
    if (completed.equals(task.getStatus()) && !PageCommentStatus.resolved.equals(comment.getStatus())) {
      commentService.get().resolve(commentId);
    }
    if (cancelled.equals(task.getStatus()) && comment != null) {
      commentService.get().delete(commentId);
    }
  }
}
