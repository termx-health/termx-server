package com.kodality.termx.thesaurus.structuredefinition;

import com.kodality.commons.exception.NotFoundException;
import com.kodality.commons.model.QueryResult;
import com.kodality.termx.auth.Authorized;
import com.kodality.termx.thesaurus.Privilege;
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

  @Authorized(Privilege.T_VIEW)
  @Get(uri = "/{id}")
  public StructureDefinition getStructureDefinition(@PathVariable Long id) {
    return service.load(id).orElseThrow(() -> new NotFoundException("Structure definition not found: " + id));
  }

  @Authorized(Privilege.T_VIEW)
  @Get(uri = "{?params*}")
  public QueryResult<StructureDefinition> queryStructureDefinitions(StructureDefinitionQueryParams params) {
    return service.query(params);
  }

  @Authorized(Privilege.T_EDIT)
  @Post
  public HttpResponse<?> saveStructureDefinition(@Body StructureDefinition structureDefinition) {
    return HttpResponse.created(service.save(structureDefinition));
  }

  @Authorized(Privilege.T_EDIT)
  @Put(uri = "/{id}")
  public HttpResponse<?> updateStructureDefinition(@PathVariable Long id,@Body StructureDefinition structureDefinition) {
    structureDefinition.setId(id);
    return HttpResponse.created(service.save(structureDefinition));
  }

  @Authorized(Privilege.T_EDIT)
  @Delete(uri = "/{id}")
  public HttpResponse<?> deleteStructureDefinition(@PathVariable Long id) {
    service.cancel(id);
    return HttpResponse.ok();
  }

}
