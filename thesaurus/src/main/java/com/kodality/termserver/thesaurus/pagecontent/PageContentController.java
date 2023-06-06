package com.kodality.termserver.thesaurus.pagecontent;

import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.auth.Authorized;
import com.kodality.termserver.thesaurus.Privilege;
import com.kodality.termserver.thesaurus.page.PageContent;
import com.kodality.termserver.thesaurus.page.PageContentQueryParams;
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
