package com.kodality.termx.wiki.task;

import com.kodality.termx.task.Task;
import com.kodality.termx.task.TaskStatus;
import com.kodality.termx.task.api.TaskStatusChangeInterceptor;
import com.kodality.termx.wiki.page.PageComment;
import com.kodality.termx.wiki.page.PageCommentStatus;
import com.kodality.termx.wiki.pagecomment.PageCommentService;
import javax.inject.Provider;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;



@Singleton
@RequiredArgsConstructor
public class WikiPageCommentTaskFlowStatusChangeInterceptor implements TaskStatusChangeInterceptor {
  private final Provider<PageCommentService> commentService;

  public static final String TASK_CTX_TYPE = "page-comment";
  public static final String TASK_WORKFLOW = "wiki-page-comment";

  @Override
  public void afterStatusChange(Task task, String previousStatus) {
    if (task == null || task.getWorkflow() == null || task.getContext() == null) {
      return;
    }
    if (!TASK_WORKFLOW.equals(task.getWorkflow())) {
      return;
    }
    Long commentId = task.getContext().stream().filter(ctx -> TASK_CTX_TYPE.equals(ctx.getType())).findFirst().map(t -> (Long) t.getId()).orElseThrow();
    PageComment comment = commentService.get().load(commentId);
    if (TaskStatus.completed.equals(task.getStatus()) && !PageCommentStatus.resolved.equals(comment.getStatus())) {
      commentService.get().resolve(commentId);
    }
    if (TaskStatus.cancelled.equals(task.getStatus()) && comment != null) {
      commentService.get().delete(commentId);
    }
  }
}
