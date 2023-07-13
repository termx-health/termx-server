package com.kodality.termx.wiki.pagerelation;

import com.kodality.commons.model.QueryResult;
import com.kodality.termx.auth.Authorized;
import com.kodality.termx.wiki.Privilege;
import com.kodality.termx.wiki.page.PageRelation;
import com.kodality.termx.wiki.page.PageRelationQueryParams;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import lombok.RequiredArgsConstructor;

@Controller("/page-relations")
@RequiredArgsConstructor
public class PageRelationController {

  private final PageRelationService pageRelationService;

  @Authorized(Privilege.T_VIEW)
  @Get(uri = "{?params*}")
  public QueryResult<PageRelation> queryPageRelations(PageRelationQueryParams params) {
    return pageRelationService.query(params);
  }
}
