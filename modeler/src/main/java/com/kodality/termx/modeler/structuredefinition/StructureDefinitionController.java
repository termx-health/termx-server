package com.kodality.termx.modeler.structuredefinition;

import com.kodality.commons.exception.NotFoundException;
import com.kodality.commons.model.QueryResult;
import com.kodality.termx.core.auth.Authorized;
import com.kodality.termx.core.auth.SessionStore;
import com.kodality.termx.modeler.Privilege;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import lombok.RequiredArgsConstructor;

@Controller("/structure-definitions")
@RequiredArgsConstructor
public class StructureDefinitionController {
  private final StructureDefinitionService service;

  @Authorized(Privilege.SD_VIEW)
  @Get(uri = "/{id}")
  public StructureDefinition getStructureDefinition(@PathVariable Long id) {
    return service.load(id).orElseThrow(() -> new NotFoundException("Structure definition not found: " + id));
  }

  @Authorized(Privilege.SD_VIEW)
  @Get(uri = "{?params*}")
  public QueryResult<StructureDefinition> queryStructureDefinitions(StructureDefinitionQueryParams params) {
    params.setPermittedIds(SessionStore.require().getPermittedResourceIds(Privilege.SD_VIEW, Long::valueOf));
    return service.query(params);
  }

  @Authorized(Privilege.SD_EDIT)
  @Post
  public HttpResponse<?> saveStructureDefinition(@Body StructureDefinition structureDefinition) {
    structureDefinition.setId(null);
    return HttpResponse.created(service.save(structureDefinition));
  }

  @Authorized(Privilege.SD_EDIT)
  @Put(uri = "/{id}")
  public HttpResponse<?> updateStructureDefinition(@PathVariable Long id, @Body StructureDefinition structureDefinition) {
    structureDefinition.setId(id);
    return HttpResponse.created(service.save(structureDefinition));
  }

  @Authorized(Privilege.SD_EDIT)
  @Delete(uri = "/{id}")
  public HttpResponse<?> deleteStructureDefinition(@PathVariable Long id) {
    service.cancel(id);
    return HttpResponse.ok();
  }

}
