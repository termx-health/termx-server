package com.kodality.termx.wiki.task;

import com.kodality.termx.task.Task;
import com.kodality.termx.task.Task.TaskActivity;
import com.kodality.termx.task.Task.TaskActivity.TaskActivityContextItem;
import com.kodality.termx.task.Task.TaskContextItem;
import com.kodality.termx.task.TaskPriority;
import com.kodality.termx.task.TaskService;
import com.kodality.termx.task.TaskStatus;
import com.kodality.termx.wiki.page.PageComment;
import com.kodality.termx.wiki.page.PageCommentStatus;
import com.kodality.termx.wiki.page.PageContent;
import com.kodality.termx.wiki.pagecomment.interceptors.PageCommentCreateInterceptor;
import com.kodality.termx.wiki.pagecomment.interceptors.PageCommentDeleteInterceptor;
import com.kodality.termx.wiki.pagecomment.interceptors.PageCommentStatusChangeInterceptor;
import com.kodality.termx.wiki.pagecomment.interceptors.PageCommentUpdateInterceptor;
import com.kodality.termx.wiki.pagecontent.PageContentService;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Provider;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;


@Singleton
@RequiredArgsConstructor
public class WikiPageCommentTaskFlowInterceptor
    implements PageCommentCreateInterceptor, PageCommentUpdateInterceptor, PageCommentDeleteInterceptor, PageCommentStatusChangeInterceptor {
  private final TaskService taskService;
  private final Provider<PageContentService> pageContentService;

  public static final String TASK_CTX_TYPE = "page-comment";
  public static final String TASK_WORKFLOW = "wiki-page-comment";

  @Override
  public void afterCommentCreate(PageComment comment) {
    saveComment(comment);
  }

  @Override
  public void afterCommentUpdate(PageComment comment) {
    saveComment(comment);
  }

  private void saveComment(PageComment comment) {
    if (comment.getParentId() != null) {
      // reply
      TaskContextItem ctx = new TaskContextItem().setId(comment.getParentId()).setType(TASK_CTX_TYPE);
      Task parentTask = taskService.findTasks(TaskService.contextString(List.of(ctx))).stream().findFirst().orElseThrow();
      taskService.saveTaskActivity(parentTask.getNumber(),
          String.format("User %s response to comment:\n%s", comment.getCreatedBy(), formatQuote(comment.getComment())),
          getActivityContext(comment));
      return;
    }

    PageContent pageContent = pageContentService.get().load(comment.getPageContentId());
    Task task = taskService.findTasks(TaskService.contextString(getContext(comment))).stream().findFirst()
        .orElseGet(() -> new Task()
            .setStatus(TaskStatus.requested)
            .setPriority(TaskPriority.routine)
        );
    task.setWorkflow(TASK_WORKFLOW);
    task.setTitle(String.format("%s was commented", pageContent.getName()));
    task.setContent(String.format(
        "User %s says on the page [%s](page:%s/%s):\n%s\n\non text:\n%s",
        comment.getCreatedBy(), pageContent.getName(), pageContent.getSpaceId(), pageContent.getSlug(),
        formatQuote(comment.getComment()), formatQuote(comment.getText())
    ));
    task.setContext(getContext(comment));
    taskService.saveTask(task);
  }


  @Override
  public void afterCommentDelete(PageComment comment) {
    if (comment.getParentId() != null) {
      // reply
      Task parentTask = taskService.findTasks(TASK_CTX_TYPE + "|" + comment.getParentId()).stream().findFirst().orElseThrow();
      TaskActivity activity = taskService.findActivities(TaskService.activityContextString(getActivityContext(comment))).stream().findFirst().orElseThrow();
      taskService.cancelTaskActivity(parentTask.getNumber(), activity.getId());
      return;
    }

    List<TaskContextItem> ctx = getContext(comment);
    taskService.cancelTasks(TaskService.contextString(ctx));
  }

  @Override
  public void afterStatusChange(PageComment comment) {
    List<TaskContextItem> ctx = getContext(comment);
    if (PageCommentStatus.resolved.equals(comment.getStatus())) {
      taskService.completeTasks(TaskService.contextString(ctx));
    }
  }

  private List<TaskContextItem> getContext(PageComment comment) {
    return List.of(new TaskContextItem().setId(comment.getId()).setType(TASK_CTX_TYPE));
  }

  private List<TaskActivityContextItem> getActivityContext(PageComment comment) {
    return List.of(new TaskActivityContextItem().setId(comment.getId()).setType(TASK_CTX_TYPE));
  }

  private String formatQuote(String text) {
    return Arrays.stream(text.split("\n")).map(l -> ">" + l).collect(Collectors.joining("\n"));
  }
}
