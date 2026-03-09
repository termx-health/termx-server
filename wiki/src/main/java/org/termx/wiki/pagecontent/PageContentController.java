package org.termx.wiki.pagecontent;

import com.kodality.commons.model.QueryResult;
import org.termx.core.auth.Authorized;
import org.termx.core.auth.SessionStore;
import org.termx.wiki.Privilege;
import org.termx.wiki.page.PageContent;
import org.termx.wiki.page.PageContentQueryParams;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import lombok.RequiredArgsConstructor;

@Controller("/page-contents")
@RequiredArgsConstructor
public class PageContentController {
  private final PageContentService pageContentService;

  @Authorized(Privilege.W_VIEW)
  @Get(uri = "{?params*}")
  public QueryResult<PageContent> queryPageContents(PageContentQueryParams params) {
    params.setPermittedSpaceIds(SessionStore.require().getPermittedResourceIds(Privilege.W_VIEW, Long::valueOf));
    return pageContentService.query(params);
  }
}
