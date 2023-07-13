package com.kodality.termx.wiki.page;

import com.kodality.commons.exception.NotFoundException;
import com.kodality.commons.model.QueryResult;
import com.kodality.termx.auth.Authorized;
import com.kodality.termx.sys.provenance.Provenance;
import com.kodality.termx.sys.provenance.ProvenanceService;
import com.kodality.termx.wiki.Privilege;
import com.kodality.termx.wiki.pagecontent.PageContentService;
import com.kodality.termx.wiki.pagelink.PageLinkService;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.validation.Validated;
import java.util.List;
import javax.validation.Valid;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@Validated
@Controller("/pages")
@RequiredArgsConstructor
public class PageController {
  private final PageService pageService;
  private final PageContentService contentService;
  private final PageLinkService linkService;
  private final ProvenanceService provenanceService;

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
  public HttpResponse<?> savePage(@Body @Valid PageRequest request) {
    Page page = pageService.save(request.getPage(), request.getContent());
    provenanceService.create(new Provenance("created", "Page", page.getId().toString()));
    return HttpResponse.created(page);
  }

  @Authorized(Privilege.T_EDIT)
  @Put(uri = "/{id}")
  public HttpResponse<?> updatePage(@PathVariable Long id, @Body @Valid PageRequest request) {
    request.getPage().setId(id);
    Page page = pageService.save(request.getPage(), request.getContent());
    provenanceService.create(new Provenance("modified", "Page", page.getId().toString()));
    return HttpResponse.created(page);
  }

  @Authorized(Privilege.T_EDIT)
  @Delete(uri = "/{id}")
  public HttpResponse<?> deletePage(@PathVariable Long id) {
    pageService.cancel(id);
    provenanceService.create(new Provenance("deleted", "Page", id.toString()));
    return HttpResponse.ok();
  }

  @Authorized(Privilege.T_EDIT)
  @Post(uri = "/{id}/contents")
  public HttpResponse<?> savePageContent(@PathVariable Long id, @Body PageContent content) {
    contentService.save(content, id);
    provenanceService.create(new Provenance("modified", "Page", id.toString()));
    return HttpResponse.created(content);
  }

  @Authorized(Privilege.T_EDIT)
  @Put(uri = "/{id}/contents/{contentId}")
  public HttpResponse<?> updatePageContent(@PathVariable Long id, @PathVariable Long contentId, @Body PageContent content) {
    content.setId(contentId);
    contentService.save(content, id);
    provenanceService.create(new Provenance("modified", "Page", id.toString()));
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
