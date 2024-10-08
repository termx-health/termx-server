package com.kodality.termx.wiki.pagerelation;

import com.kodality.commons.model.QueryResult;
import com.kodality.termx.core.auth.Authorized;
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

  @Authorized(Privilege.W_VIEW)
  @Get(uri = "{?params*}")
  public QueryResult<PageRelation> queryPageRelations(PageRelationQueryParams params) {
    //TODO auth
    return pageRelationService.query(params);
  }
}
