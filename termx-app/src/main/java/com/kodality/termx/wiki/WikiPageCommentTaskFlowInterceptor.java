package com.kodality.termx.wiki;

import com.kodality.taskflow.task.Task;
import com.kodality.taskflow.task.Task.TaskContextItem;
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
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;

import static com.kodality.termx.taskflow.TaskFlowService.contextString;


@Singleton
@RequiredArgsConstructor
public class WikiPageCommentTaskFlowInterceptor
    implements PageCommentCreateInterceptor, PageCommentUpdateInterceptor, PageCommentDeleteInterceptor, PageCommentStatusChangeInterceptor {
  private final TaskFlowService taskFlowService;
  private final PageContentService pageContentService;

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
      // ignore replies
      return;
    }

    PageContent pageContent = pageContentService.load(comment.getPageContentId());
    Task task = new Task();
    task.setTitle(String.format("%s was commented", pageContent.getName()));
    task.setContent(String.format(
        "User %s says:\n%s\n\non text:\n%s",
        comment.getCreatedBy(), formatQuote(comment.getComment()), formatQuote(comment.getText())
    ));
    task.setContext(getContext(comment));
    taskFlowService.createTask(task, TASK_WORKFLOW);
  }

  @Override
  public void afterCommentDelete(PageComment comment) {
    if (comment.getParentId() == null) {
      List<TaskContextItem> ctx = getContext(comment);
      taskFlowService.cancelTasks(contextString(ctx));
    }
  }

  @Override
  public void afterStatusChange(PageComment comment) {
    List<TaskContextItem> ctx = getContext(comment);
    if (PageCommentStatus.resolved.equals(comment.getStatus())) {
      taskFlowService.completeTasks(contextString(ctx));
    }
  }


  private static List<TaskContextItem> getContext(PageComment comment) {
    return List.of(new TaskContextItem().setId(comment.getId()).setType(TASK_CTX_TYPE));
  }

  private String formatQuote(String text) {
    return Arrays.stream(text.split("\n")).map(l -> ">" + l).collect(Collectors.joining("\n"));
  }
}
