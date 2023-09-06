package com.kodality.termx.wiki.pagecomment;

import com.kodality.commons.model.QueryResult;
import com.kodality.termx.wiki.page.PageComment;
import com.kodality.termx.wiki.page.PageCommentQueryParams;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.validation.Validated;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;

@Validated
@RequiredArgsConstructor
@Controller("/page-comments")
public class PageCommentController {
  private final PageCommentService commentService;

  @Get("{?params*}")
  public QueryResult<PageComment> query(PageCommentQueryParams params) {
    return commentService.query(params);
  }

  @Post
  public HttpResponse<?> create(@Body @Valid PageComment comment) {
    comment.setId(null);
    return HttpResponse.created(commentService.create(comment));
  }

  @Put("/{id}")
  public HttpResponse<?> update(@Parameter Long id, @Body @Valid PageComment comment) {
    comment.setId(id);
    return HttpResponse.ok(commentService.update(comment));
  }

  @Delete("/{id}")
  public HttpResponse<?> delete(@Parameter Long id) {
    commentService.delete(id);
    return HttpResponse.noContent();
  }

  @Post("/{id}/resolve")
  public HttpResponse<?> resolve(@Parameter Long id) {
    return HttpResponse.ok(commentService.resolve(id));
  }
}
