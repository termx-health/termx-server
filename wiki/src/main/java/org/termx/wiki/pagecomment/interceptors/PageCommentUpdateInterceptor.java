package org.termx.wiki.pagecomment.interceptors;

import org.termx.wiki.page.PageComment;

public interface PageCommentUpdateInterceptor {
  void afterCommentUpdate(PageComment comment);
}
