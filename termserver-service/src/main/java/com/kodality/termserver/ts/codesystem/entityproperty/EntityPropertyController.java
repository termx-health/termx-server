package com.kodality.termserver.ts.codesystem.entityproperty;

import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.codesystem.EntityProperty;
import com.kodality.termserver.codesystem.EntityPropertyQueryParams;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import lombok.RequiredArgsConstructor;

@Controller("/ts/entity-properties")
@RequiredArgsConstructor
public class EntityPropertyController {
  private final EntityPropertyService entityPropertyService;

  @Get(uri = "{?params*}")
  public QueryResult<EntityProperty> getEntityProperties(EntityPropertyQueryParams params) {
    return entityPropertyService.query(params);
  }

}
