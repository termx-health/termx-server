package com.kodality.termx.terminology.terminology.definedproperty;

import com.kodality.commons.exception.NotFoundException;
import com.kodality.commons.model.QueryResult;
import com.kodality.termx.core.auth.Authorized;
import com.kodality.termx.terminology.Privilege;
import com.kodality.termx.ts.property.DefinedProperty;
import com.kodality.termx.ts.property.DefinedPropertyQueryParams;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;

@Controller("/ts/defined-properties")
@RequiredArgsConstructor
public class DefinedPropertyController {
  private final DefinedPropertyService service;

  @Authorized(Privilege.DEF_PROP_VIEW)
  @Get(uri = "{?params*}")
  public QueryResult<DefinedProperty> query(DefinedPropertyQueryParams params) {
    return service.query(params);
  }

  @Authorized(Privilege.DEF_PROP_VIEW)
  @Get(uri = "/{id}")
  public DefinedProperty load(@PathVariable Long id) {
    return service.load(id).orElseThrow(() -> new NotFoundException("Defined entity property not found: " + id));
  }

  @Authorized(Privilege.DEF_PROP_EDIT)
  @Post
  public HttpResponse<?> create(@Body @Valid DefinedProperty entityProperty) {
    entityProperty.setId(null);
    service.save(entityProperty);
    return HttpResponse.created(entityProperty);
  }

  @Authorized(Privilege.DEF_PROP_EDIT)
  @Put("/{id}")
  public HttpResponse<?> update(@PathVariable Long id, @Body @Valid DefinedProperty entityProperty) {
    entityProperty.setId(id);
    service.save(entityProperty);
    return HttpResponse.ok();
  }

  @Authorized(Privilege.DEF_PROP_EDIT)
  @Post("/{id}/update-related")
  public HttpResponse<?> updateRelated(@PathVariable Long id) {
    service.updateRelated(id);
    return HttpResponse.ok();
  }

}
