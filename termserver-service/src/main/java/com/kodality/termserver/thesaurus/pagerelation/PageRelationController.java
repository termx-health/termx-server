package com.kodality.termserver.thesaurus.pagerelation;

import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.thesaurus.page.PageRelation;
import com.kodality.termserver.thesaurus.page.PageRelationQueryParams;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import lombok.RequiredArgsConstructor;

@Controller("/page-relations")
@RequiredArgsConstructor
public class PageRelationController {

  private final PageRelationService pageRelationService;

  @Get(uri = "{?params*}")
  public QueryResult<PageRelation> queryPageRelations(PageRelationQueryParams params) {
    return pageRelationService.query(params);
  }
}
