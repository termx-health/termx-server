package com.kodality.termserver.thesaurus.pagecontent;

import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.thesaurus.page.PageContent;
import com.kodality.termserver.thesaurus.page.PageContentQueryParams;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import lombok.RequiredArgsConstructor;

@Controller("/page-contents")
@RequiredArgsConstructor
public class PageContentController {
  private final PageContentService pageContentService;

  @Get(uri = "{?params*}")
  public QueryResult<PageContent> queryPages(PageContentQueryParams params) {
    return pageContentService.query(params);
  }
}
