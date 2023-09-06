package com.kodality.termx.wiki.pagecomment.interceptors;

import com.kodality.termx.wiki.page.PageComment;

public interface PageCommentDeleteInterceptor {
  void afterCommentDelete(PageComment comment);
}
