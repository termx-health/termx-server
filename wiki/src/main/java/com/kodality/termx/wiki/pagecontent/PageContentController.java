package com.kodality.termx.wiki.pagecontent;

import com.kodality.commons.model.QueryResult;
import com.kodality.termx.auth.Authorized;
import com.kodality.termx.wiki.Privilege;
import com.kodality.termx.wiki.page.PageContent;
import com.kodality.termx.wiki.page.PageContentQueryParams;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import lombok.RequiredArgsConstructor;

@Controller("/page-contents")
@RequiredArgsConstructor
public class PageContentController {
  private final PageContentService pageContentService;

  @Authorized(Privilege.T_VIEW)
  @Get(uri = "{?params*}")
  public QueryResult<PageContent> queryPageContents(PageContentQueryParams params) {
    return pageContentService.query(params);
  }
}
