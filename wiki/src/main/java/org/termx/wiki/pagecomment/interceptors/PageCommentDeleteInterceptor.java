package org.termx.wiki.pagecomment.interceptors;

import org.termx.wiki.page.PageComment;

public interface PageCommentDeleteInterceptor {
  void afterCommentDelete(PageComment comment);
}
