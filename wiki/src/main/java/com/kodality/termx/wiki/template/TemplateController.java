package com.kodality.termx.wiki.template;

import com.kodality.commons.exception.NotFoundException;
import com.kodality.commons.model.QueryResult;
import com.kodality.termx.core.auth.Authorized;
import com.kodality.termx.wiki.Privilege;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import lombok.RequiredArgsConstructor;

@Controller("/templates")
@RequiredArgsConstructor
public class TemplateController {
  private final TemplateService service;

  @Authorized(privilege = Privilege.W_VIEW)
  @Get(uri = "/{id}")
  public Template getTemplate(@PathVariable Long id) {
    return service.load(id).orElseThrow(() -> new NotFoundException("Template not found: " + id));
  }

  @Authorized(privilege = Privilege.W_VIEW)
  @Get(uri = "{?params*}")
  public QueryResult<Template> queryTemplates(TemplateQueryParams params) {
    return service.query(params);
  }

  @Authorized(privilege = Privilege.W_EDIT)
  @Post
  public HttpResponse<?> saveTemplate(@Body Template template) {
    return HttpResponse.created(service.save(template));
  }

  @Authorized(privilege = Privilege.W_EDIT)
  @Put(uri = "/{id}")
  public HttpResponse<?> updateTemplate(@PathVariable Long id,@Body Template template) {
    template.setId(id);
    return HttpResponse.created(service.save(template));
  }

  @Authorized(privilege = Privilege.W_EDIT)
  @Delete(uri = "/{id}")
  public HttpResponse<?> deleteTemplate(@PathVariable Long id) {
    service.cancel(id);
    return HttpResponse.ok();
  }
}
