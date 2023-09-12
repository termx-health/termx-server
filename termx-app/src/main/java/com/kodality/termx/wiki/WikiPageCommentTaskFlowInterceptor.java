package com.kodality.termx.wiki;

import com.kodality.taskflow.task.Task;
import com.kodality.taskflow.task.Task.TaskContextItem;
import com.kodality.taskflow.task.activity.TaskActivity;
import com.kodality.taskflow.task.activity.TaskActivity.TaskActivityContextItem;
import com.kodality.termx.taskflow.TaskFlowService;
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

import static com.kodality.termx.taskflow.TaskFlowService.activityContextString;
import static com.kodality.termx.taskflow.TaskFlowService.contextString;


@Singleton
@RequiredArgsConstructor
public class WikiPageCommentTaskFlowInterceptor
    implements PageCommentCreateInterceptor, PageCommentUpdateInterceptor, PageCommentDeleteInterceptor, PageCommentStatusChangeInterceptor {
  private final TaskFlowService taskFlowService;
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
      Task parentTask = taskFlowService.findTasks(contextString(List.of(ctx))).stream().findFirst().orElseThrow();

      TaskActivity activity = new TaskActivity();
      activity.setTaskId(parentTask.getId());
      activity.setContext(getActivityContext(comment));
      activity.setNote(String.format("User %s response to comment:\n%s", comment.getCreatedBy(), formatQuote(comment.getComment())));
      taskFlowService.createTaskActivity(activity);
      return;
    }

    PageContent pageContent = pageContentService.get().load(comment.getPageContentId());
    Task task = new Task();
    task.setTitle(String.format("%s was commented", pageContent.getName()));
    task.setContent(String.format(
        "User %s says on the page [%s](page:%s/%s):\n%s\n\non text:\n%s",
        comment.getCreatedBy(), pageContent.getName(), pageContent.getSpaceId(), pageContent.getSlug(),
        formatQuote(comment.getComment()), formatQuote(comment.getText())
    ));
    task.setContext(getContext(comment));
    taskFlowService.createTask(task, TASK_WORKFLOW);
  }


  @Override
  public void afterCommentDelete(PageComment comment) {
    if (comment.getParentId() != null) {
      // reply
      List<TaskActivityContextItem> ctx = getActivityContext(comment);
      TaskActivity activity = taskFlowService.findActivities(activityContextString(ctx)).stream().findFirst().orElseThrow();
      taskFlowService.cancelTaskActivity(activity.getId());
      return;
    }

    List<TaskContextItem> ctx = getContext(comment);
    taskFlowService.cancelTasks(contextString(ctx));
  }

  @Override
  public void afterStatusChange(PageComment comment) {
    List<TaskContextItem> ctx = getContext(comment);
    if (PageCommentStatus.resolved.equals(comment.getStatus())) {
      taskFlowService.completeTasks(contextString(ctx));
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
