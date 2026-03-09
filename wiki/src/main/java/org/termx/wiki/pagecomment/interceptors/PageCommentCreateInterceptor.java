package org.termx.wiki.pagecomment.interceptors;

import org.termx.wiki.page.PageComment;

public interface PageCommentCreateInterceptor {
  void afterCommentCreate(PageComment comment);
}
