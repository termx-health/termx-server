package com.kodality.termx.wiki.pagecomment.interceptors;

import com.kodality.termx.wiki.page.PageComment;
import java.util.List;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class PageCommentInterceptorService {
  private final List<PageCommentCreateInterceptor> createInterceptor;
  private final List<PageCommentUpdateInterceptor> updateInterceptor;
  private final List<PageCommentDeleteInterceptor> deleteInterceptor;
  private final List<PageCommentStatusChangeInterceptor> statusChangeInterceptor;

  public void afterCommentCreate(PageComment comment) {
    createInterceptor.forEach(i -> i.afterCommentCreate(comment));
  }

  public void afterCommentUpdate(PageComment comment) {
    updateInterceptor.forEach(i -> i.afterCommentUpdate(comment));
  }

  public void afterCommentDelete(PageComment comment) {
    deleteInterceptor.forEach(i -> i.afterCommentDelete(comment));
  }

  public void afterStatusChange(PageComment comment) {
    statusChangeInterceptor.forEach(i -> i.afterStatusChange(comment));
  }
}
