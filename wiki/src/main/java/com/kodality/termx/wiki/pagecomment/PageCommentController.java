package com.kodality.termx.wiki.pagecomment;

import com.kodality.commons.model.QueryResult;
import com.kodality.termx.core.auth.Authorized;
import com.kodality.termx.core.auth.SessionStore;
import com.kodality.termx.wiki.Privilege;
import com.kodality.termx.wiki.page.PageComment;
import com.kodality.termx.wiki.page.PageCommentQueryParams;
import com.kodality.termx.wiki.pagecontent.PageContentService;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.validation.Validated;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Validated
@RequiredArgsConstructor
@Controller("/page-comments")
public class PageCommentController {
  private final PageContentService pageContentService;
  private final PageCommentService commentService;

  @Authorized(Privilege.W_VIEW)
  @Get("{?params*}")
  public QueryResult<PageComment> query(PageCommentQueryParams params) {
    params.setPermittedSpaceIds(SessionStore.require().getPermittedResourceIds(Privilege.W_VIEW, Long::valueOf));
    return commentService.query(params);
  }

  @Authorized(Privilege.W_EDIT)
  @Post
  public HttpResponse<?> create(@Body @Valid PageComment comment) {
    comment.setId(null);
    return HttpResponse.created(commentService.create(comment));
  }

  @Authorized(privilege = Privilege.W_EDIT)
  @Put("/{id}")
  public HttpResponse<?> update(@PathVariable Long id, @Body @Valid PageComment comment) {
    SessionStore.require().checkPermitted(pageContentService.load(comment.getPageContentId()).getSpaceId().toString(), Privilege.W_VIEW);
    comment.setId(id);
    return HttpResponse.ok(commentService.update(comment));
  }

  @Authorized(privilege = Privilege.W_EDIT)
  @Delete("/{id}")
  public HttpResponse<?> delete(@PathVariable Long id) {
    PageComment comment = commentService.load(id);
    SessionStore.require().checkPermitted(pageContentService.load(comment.getPageContentId()).getSpaceId().toString(), Privilege.W_EDIT);
    commentService.delete(id);
    return HttpResponse.noContent();
  }

  @Authorized(privilege = Privilege.W_EDIT)
  @Post("/{id}/resolve")
  public HttpResponse<?> resolve(@PathVariable Long id) {
    PageComment comment = commentService.load(id);
    SessionStore.require().checkPermitted(pageContentService.load(comment.getPageContentId()).getSpaceId().toString(), Privilege.W_EDIT);
    return HttpResponse.ok(commentService.resolve(id));
  }
}
