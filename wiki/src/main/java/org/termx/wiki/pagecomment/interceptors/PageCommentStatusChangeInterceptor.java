package org.termx.wiki.pagecomment.interceptors;

import org.termx.wiki.page.PageComment;

public interface PageCommentStatusChangeInterceptor {
  void afterStatusChange(PageComment comment);
}
