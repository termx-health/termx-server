package com.kodality.termx.terminology.definedproperty;

import com.kodality.commons.exception.NotFoundException;
import com.kodality.commons.model.QueryResult;
import com.kodality.termx.Privilege;
import com.kodality.termx.auth.Authorized;
import com.kodality.termx.auth.ResourceId;
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

  @Authorized(Privilege.CS_VIEW)
  @Get(uri = "{?params*}")
  public QueryResult<DefinedProperty> query(DefinedPropertyQueryParams params) {
    return service.query(params);
  }

  @Authorized(Privilege.CS_VIEW)
  @Get(uri = "/{id}")
  public DefinedProperty load(@PathVariable Long id) {
    return service.load(id).orElseThrow(() -> new NotFoundException("Defined entity property not found: " + id));
  }

  @Authorized(Privilege.CS_EDIT)
  @Post
  public HttpResponse<?> create(@Body @Valid DefinedProperty entityProperty) {
    entityProperty.setId(null);
    service.save(entityProperty);
    return HttpResponse.created(entityProperty);
  }

  @Authorized(Privilege.CS_EDIT)
  @Put("/{id}")
  public HttpResponse<?> update(@PathVariable @ResourceId Long id, @Body @Valid DefinedProperty entityProperty) {
    entityProperty.setId(id);
    service.save(entityProperty);
    return HttpResponse.ok();
  }

}
