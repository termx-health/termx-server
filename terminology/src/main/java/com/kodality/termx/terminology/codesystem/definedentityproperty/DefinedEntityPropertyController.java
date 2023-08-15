package com.kodality.termx.terminology.codesystem.definedentityproperty;

import com.kodality.commons.exception.NotFoundException;
import com.kodality.commons.model.QueryResult;
import com.kodality.termx.Privilege;
import com.kodality.termx.auth.Authorized;
import com.kodality.termx.auth.ResourceId;
import com.kodality.termx.ts.codesystem.DefinedEntityProperty;
import com.kodality.termx.ts.codesystem.DefinedEntityPropertyQueryParams;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;

@Controller("/ts/defined-entity-properties")
@RequiredArgsConstructor
public class DefinedEntityPropertyController {
  private final DefinedEntityPropertyService service;

  @Authorized(Privilege.CS_VIEW)
  @Get(uri = "{?params*}")
  public QueryResult<DefinedEntityProperty> query(DefinedEntityPropertyQueryParams params) {
    return service.query(params);
  }

  @Authorized(Privilege.CS_VIEW)
  @Get(uri = "/{id}")
  public DefinedEntityProperty load(@PathVariable Long id) {
    return service.load(id).orElseThrow(() -> new NotFoundException("Defined entity property not found: " + id));
  }

  @Authorized(Privilege.CS_EDIT)
  @Post
  public HttpResponse<?> create(@Body @Valid DefinedEntityProperty entityProperty) {
    entityProperty.setId(null);
    service.save(entityProperty);
    return HttpResponse.created(entityProperty);
  }

  @Authorized(Privilege.CS_EDIT)
  @Put("/{id}")
  public HttpResponse<?> update(@PathVariable @ResourceId Long id, @Body @Valid DefinedEntityProperty entityProperty) {
    entityProperty.setId(id);
    service.save(entityProperty);
    return HttpResponse.ok();
  }

}
