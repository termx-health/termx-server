package org.termx.modeler.structuredefinition;

import org.termx.modeler.structuredefinition.StructureDefinition;
import org.termx.modeler.structuredefinition.StructureDefinitionImportRequest;
import org.termx.modeler.structuredefinition.StructureDefinitionQueryParams;
import org.termx.modeler.structuredefinition.StructureDefinitionVersion;

import com.kodality.commons.exception.NotFoundException;
import com.kodality.commons.model.QueryResult;
import org.termx.core.auth.Authorized;
import org.termx.core.auth.SessionStore;
import org.termx.modeler.Privilege;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.core.annotation.Nullable;
import lombok.RequiredArgsConstructor;

@Controller("/structure-definitions")
@RequiredArgsConstructor
public class StructureDefinitionController {
  private final StructureDefinitionService service;
  private final StructureDefinitionFhirImportService importService;

  @Authorized(Privilege.SD_VIEW)
  @Get(uri = "/{id}{?version}")
  public StructureDefinition getStructureDefinition(@PathVariable Long id, @Nullable @QueryValue String version) {
    return service.load(id, version).orElseThrow(() -> new NotFoundException("Structure definition not found: " + id));
  }

  @Authorized(Privilege.SD_VIEW)
  @Get(uri = "/{id}/versions")
  public java.util.List<StructureDefinitionVersion> listVersions(@PathVariable Long id) {
    return service.listVersions(id);
  }

  @Authorized(Privilege.SD_VIEW)
  @Get(uri = "/{id}/versions/{version}")
  public StructureDefinition getStructureDefinitionVersion(@PathVariable Long id, @PathVariable String version) {
    return service.load(id, version).orElseThrow(() -> new NotFoundException("Structure definition version not found: " + id + "/" + version));
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

  @Authorized(Privilege.SD_EDIT)
  @Post(uri = "/import")
  public HttpResponse<StructureDefinition> importStructureDefinition(@Body StructureDefinitionImportRequest request) {
    if (request.getUrl() != null && !request.getUrl().isBlank()) {
      return HttpResponse.created(importService.importFromUrl(request.getUrl()));
    }
    if (request.getContent() != null && !request.getContent().isBlank()) {
      String format = request.getFormat() != null ? request.getFormat().toLowerCase() : "json";
      if ("fsh".equals(format)) {
        throw new UnsupportedOperationException("FSH import not yet implemented");
      }
      return HttpResponse.created(importService.importFromJson(request.getContent()));
    }
    throw new IllegalArgumentException("Either url or content must be provided");
  }

}
