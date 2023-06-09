package com.kodality.termserver.thesaurus.page;

import com.kodality.commons.exception.NotFoundException;
import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.auth.Authorized;
import com.kodality.termserver.thesaurus.Privilege;
import com.kodality.termserver.thesaurus.pagecontent.PageContentService;
import com.kodality.termserver.thesaurus.pagelink.PageLinkService;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@Controller("/pages")
@RequiredArgsConstructor
public class PageController {
  private final PageService pageService;
  private final PageContentService contentService;
  private final PageLinkService linkService;

  @Authorized(Privilege.T_VIEW)
  @Get(uri = "/{id}")
  public Page getPage(@PathVariable Long id) {
    return pageService.load(id).orElseThrow(() -> new NotFoundException("Page not found: " + id));
  }

  @Authorized(Privilege.T_VIEW)
  @Get(uri = "{?params*}")
  public QueryResult<Page> queryPages(PageQueryParams params) {
    return pageService.query(params);
  }

  @Authorized(Privilege.T_EDIT)
  @Post
  public HttpResponse<?> savePage(@Body PageRequest request) {
    return HttpResponse.created(pageService.save(request.getPage(), request.getContent()));
  }

  @Authorized(Privilege.T_EDIT)
  @Put(uri = "/{id}")
  public HttpResponse<?> updatePage(@PathVariable Long id, @Body PageRequest request) {
    request.getPage().setId(id);
    return HttpResponse.created(pageService.save(request.getPage(), request.getContent()));
  }

  @Authorized(Privilege.T_EDIT)
  @Delete(uri = "/{id}")
  public HttpResponse<?> deletePage(@PathVariable Long id) {
    pageService.cancel(id);
    return HttpResponse.ok();
  }

  @Authorized(Privilege.T_EDIT)
  @Post(uri = "/{id}/contents")
  public HttpResponse<?> savePageContent(@PathVariable Long id, @Body PageContent content) {
    contentService.save(content, id);
    return HttpResponse.created(content);
  }

  @Authorized(Privilege.T_EDIT)
  @Put(uri = "/{id}/contents/{contentId}")
  public HttpResponse<?> updatePageContent(@PathVariable Long id, @PathVariable Long contentId, @Body PageContent content) {
    content.setId(contentId);
    contentService.save(content, id);
    return HttpResponse.created(content);
  }

  @Authorized(Privilege.T_VIEW)
  @Get(uri = "/{id}/path")
  public List<Long> getPath(@PathVariable Long id) {
    return linkService.getPath(id);
  }

  @Getter
  @Setter
  public static class PageRequest {
    private Page page;
    private PageContent content;
  }
}
