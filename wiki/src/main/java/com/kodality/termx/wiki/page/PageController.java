package com.kodality.termx.wiki.page;

import com.kodality.commons.exception.NotFoundException;
import com.kodality.commons.micronaut.rest.MultipartBodyReader;
import com.kodality.commons.model.QueryResult;
import com.kodality.termx.core.auth.Authorized;
import com.kodality.termx.core.auth.SessionStore;
import com.kodality.termx.core.sys.provenance.Provenance;
import com.kodality.termx.core.sys.provenance.ProvenanceService;
import com.kodality.termx.wiki.Privilege;
import com.kodality.termx.wiki.page.Page.PageAttachment;
import com.kodality.termx.wiki.pageattachment.PageAttachmentService;
import com.kodality.termx.wiki.pagecontent.PageContentService;
import com.kodality.termx.wiki.pagelink.PageLinkService;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.server.multipart.MultipartBody;
import io.micronaut.http.server.types.files.StreamedFile;
import io.micronaut.validation.Validated;
import java.util.List;
import java.util.Map;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@Validated
@Controller("/pages")
@RequiredArgsConstructor
public class PageController {
  private final PageService pageService;
  private final PageContentService contentService;
  private final PageAttachmentService attachmentService;
  private final PageLinkService linkService;
  private final ProvenanceService provenanceService;

  @Authorized(privilege = Privilege.W_VIEW)
  @Get("/{id}")
  public Page getPage(@PathVariable Long id) {
    Page page = pageService.load(id).orElseThrow(() -> new NotFoundException("Page not found: " + id));
    SessionStore.require().checkPermitted(page.getSpaceId().toString(), Privilege.W_VIEW);
    return page;
  }

  @Authorized(Privilege.W_VIEW)
  @Get("{?params*}")
  public QueryResult<Page> queryPages(PageQueryParams params) {
    params.setPermittedSpaceIds(SessionStore.require().getPermittedResourceIds(Privilege.W_VIEW, Long::valueOf));
    return pageService.query(params);
  }

  @Authorized(Privilege.W_VIEW)
  @Get("/tree")
  public List<PageTreeItem> loadTree(@NotNull @QueryValue Long spaceId) {
    return pageService.loadTree(spaceId);
  }

  @Authorized(privilege = Privilege.W_EDIT)
  @Post
  public HttpResponse<?> createPage(@Body @Valid PageRequest request) {
    SessionStore.require().checkPermitted(request.getPage().getSpaceId().toString(), Privilege.W_EDIT);
    request.getPage().setId(null);
    Page page = pageService.save(request.getPage(), request.getContent());
    provenanceService.create(new Provenance("created", "Page", page.getId().toString()));
    return HttpResponse.created(page);
  }

  @Authorized(privilege = Privilege.W_EDIT)
  @Put("/{id}")
  public HttpResponse<?> updatePage(@PathVariable Long id, @Body @Valid PageRequest request) {
    SessionStore.require().checkPermitted(request.getPage().getSpaceId().toString(), Privilege.W_EDIT);
    request.getPage().setId(id);
    Page page = pageService.save(request.getPage(), request.getContent());
    provenanceService.create(new Provenance("modified", "Page", page.getId().toString()));
    return HttpResponse.created(page);
  }

  @Authorized(privilege = Privilege.W_EDIT)
  @Delete("/{id}")
  public HttpResponse<?> deletePage(@PathVariable Long id) {
    validate(id, Privilege.W_EDIT);
    pageService.delete(id);
    provenanceService.create(new Provenance("deleted", "Page", id.toString()));
    return HttpResponse.ok();
  }

  @Authorized(privilege = Privilege.W_VIEW)
  @Get("/{id}/path")
  public List<Long> getPath(@PathVariable Long id) {
    validate(id, Privilege.W_VIEW);
    return linkService.getPath(id);
  }


  /* Content */

  @Authorized(privilege = Privilege.W_EDIT)
  @Post("/{id}/contents")
  public HttpResponse<?> savePageContent(@PathVariable Long id, @Body PageContent content) {
    validate(id, Privilege.W_EDIT);
    PageContent persisted = contentService.save(content, id);
    provenanceService.create(new Provenance("modified", "Page", id.toString()));
    return HttpResponse.created(persisted);
  }

  @Authorized(privilege = Privilege.W_EDIT)
  @Put("/{id}/contents/{contentId}")
  public HttpResponse<?> updatePageContent(@PathVariable Long id, @PathVariable Long contentId, @Body PageContent content) {
    validate(id, Privilege.W_EDIT);
    PageContent persisted = contentService.save(content.setId(contentId), id);
    provenanceService.create(new Provenance("modified", "Page", id.toString()));
    return HttpResponse.created(persisted);
  }

  @Authorized(privilege = Privilege.W_VIEW)
  @Get("/{id}/contents/{contentId}/history{?params*}")
  public QueryResult<PageContentHistoryItem> queryPageContentHistory(@PathVariable Long id, @PathVariable Long contentId, PageContentHistoryQueryParams params) {
    validate(id, Privilege.W_VIEW);
    params.setPermittedPageIds(List.of(id));
    params.setPermittedPageContentIds(List.of(contentId));
    return contentService.queryHistory(params);
  }


  /* Files */

  @Authorized(privilege = Privilege.W_EDIT)
  @Post(value = "/{id}/files", consumes = MediaType.MULTIPART_FORM_DATA)
  public Map<String, PageAttachment> uploadFiles(@PathVariable Long id, @Body MultipartBody partz) {
    validate(id, Privilege.W_EDIT);
    MultipartBodyReader.MultipartBody body = MultipartBodyReader.readMultipart(partz);
    return attachmentService.saveAttachments(id, body.getAttachments());
  }

  @Authorized(privilege = Privilege.W_VIEW)
  @Get("/{id}/files")
  public List<PageAttachment> getFiles(@PathVariable Long id) {
    validate(id, Privilege.W_VIEW);
    return attachmentService.getAttachments(id);
  }

  @Authorized(privilege = Privilege.W_VIEW)
  @Get("/{id}/files/{fileName}")
  public StreamedFile getFile(@PathVariable Long id, @PathVariable String fileName) {
    return attachmentService.getAttachmentContent(id, fileName);
  }

  @Authorized(privilege = Privilege.W_EDIT)
  @Delete("/{id}/files/{fileName}")
  public void deleteFile(@PathVariable Long id, @PathVariable String fileName) {
    validate(id, Privilege.W_EDIT);
    attachmentService.deleteAttachmentContent(id, fileName);
  }


  private void validate(Long id, String privilege) {
    Page page = pageService.load(id).orElseThrow(() -> new NotFoundException("Page not found: " + id));
    SessionStore.require().checkPermitted(page.getSpaceId().toString(), privilege);
  }

  @Getter
  @Setter
  public static class PageRequest {
    @Valid
    private Page page;
    @Valid
    private PageContent content;
  }
}
