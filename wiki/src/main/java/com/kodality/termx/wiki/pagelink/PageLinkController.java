package com.kodality.termx.wiki.pagelink;

import com.kodality.commons.model.QueryResult;
import com.kodality.termx.auth.Authorized;
import com.kodality.termx.wiki.Privilege;
import com.kodality.termx.wiki.page.PageLink;
import com.kodality.termx.wiki.page.PageLinkMoveRequest;
import com.kodality.termx.wiki.page.PageLinkQueryParams;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import java.util.List;
import lombok.RequiredArgsConstructor;

@Controller("/page-links")
@RequiredArgsConstructor
public class PageLinkController {
  private final PageLinkService pageLinkService;

  @Authorized(Privilege.T_VIEW)
  @Get(uri = "{?params*}")
  public QueryResult<PageLink> queryPages(PageLinkQueryParams params) {
    return pageLinkService.query(params);
  }

  @Authorized(Privilege.T_EDIT)
  @Post(uri = "/{id}/move")
  public List<PageLink> movePage(@PathVariable Long id, @Body PageLinkMoveRequest req) {
    return pageLinkService.moveLink(id, req);
  }
}
