package com.kodality.termx.thesaurus.pagerelation;

import com.kodality.commons.model.QueryResult;
import com.kodality.termx.auth.Authorized;
import com.kodality.termx.thesaurus.Privilege;
import com.kodality.termx.thesaurus.page.PageRelation;
import com.kodality.termx.thesaurus.page.PageRelationQueryParams;
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
