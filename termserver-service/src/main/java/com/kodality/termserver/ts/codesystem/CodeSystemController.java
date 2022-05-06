package com.kodality.termserver.ts.codesystem;

import com.kodality.commons.exception.NotFoundException;
import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.codesystem.CodeSystem;
import com.kodality.termserver.codesystem.CodeSystemEntityVersion;
import com.kodality.termserver.codesystem.CodeSystemEntityVersionQueryParams;
import com.kodality.termserver.codesystem.CodeSystemQueryParams;
import com.kodality.termserver.codesystem.CodeSystemSupplement;
import com.kodality.termserver.codesystem.CodeSystemVersion;
import com.kodality.termserver.codesystem.Concept;
import com.kodality.termserver.codesystem.ConceptQueryParams;
import com.kodality.termserver.codesystem.EntityProperty;
import com.kodality.termserver.ts.codesystem.concept.ConceptService;
import com.kodality.termserver.ts.codesystem.entity.CodeSystemEntityVersionService;
import com.kodality.termserver.ts.codesystem.entityproperty.EntityPropertyService;
import com.kodality.termserver.ts.codesystem.supplement.CodeSystemSupplementService;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import java.util.List;
import javax.validation.Valid;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@Controller("/ts/code-systems")
@RequiredArgsConstructor
public class CodeSystemController {
  private final ConceptService conceptService;
  private final CodeSystemService codeSystemService;
  private final EntityPropertyService entityPropertyService;
  private final CodeSystemVersionService codeSystemVersionService;
  private final CodeSystemSupplementService codeSystemSupplementService;
  private final CodeSystemEntityVersionService codeSystemEntityVersionService;

  @Get(uri = "{?params*}")
  public QueryResult<CodeSystem> getCodeSystems(CodeSystemQueryParams params) {
    return codeSystemService.query(params);
  }

  @Get(uri = "/{codeSystem}")
  public CodeSystem getCodeSystem(@PathVariable String codeSystem) {
    return codeSystemService.get(codeSystem).orElseThrow(() -> new NotFoundException("CodeSystem not found: " + codeSystem));
  }

  @Post
  public HttpResponse<?> create(@Body @Valid CodeSystem codeSystem) {
    codeSystemService.create(codeSystem);
    return HttpResponse.created(codeSystem);
  }

  @Get(uri = "/{codeSystem}/versions")
  public List<CodeSystemVersion> getCodeSystemVersions(@PathVariable String codeSystem) {
    return codeSystemVersionService.getVersions(codeSystem);
  }

  @Get(uri = "/{codeSystem}/versions/{version}")
  public CodeSystemVersion getCodeSystemVersion(@PathVariable String codeSystem, @PathVariable String version) {
    return codeSystemVersionService.getVersion(codeSystem, version).orElseThrow(() -> new NotFoundException("CodeSystem version not found: " + codeSystem));
  }

  @Post(uri = "/{codeSystem}/versions")
  public HttpResponse<?> createVersion(@PathVariable String codeSystem, @Body @Valid CodeSystemVersion version) {
    version.setId(null);
    version.setCodeSystem(codeSystem);
    codeSystemVersionService.save(version);
    return HttpResponse.created(version);
  }

  @Put(uri = "/{codeSystem}/versions/{id}")
  public HttpResponse<?> updateVersion(@PathVariable String codeSystem, @PathVariable Long id, @Body @Valid CodeSystemVersion version) {
    version.setId(id);
    version.setCodeSystem(codeSystem);
    codeSystemVersionService.save(version);
    return HttpResponse.created(version);
  }

  @Post(uri = "/{codeSystem}/versions/{version}/activate")
  public HttpResponse<?> activateVersion(@PathVariable String codeSystem, @PathVariable String version) {
    codeSystemVersionService.activate(codeSystem, version);
    return HttpResponse.noContent();
  }

  @Post(uri = "/{codeSystem}/versions/{version}/retire")
  public HttpResponse<?> retireVersion(@PathVariable String codeSystem, @PathVariable String version) {
    codeSystemVersionService.retire(codeSystem, version);
    return HttpResponse.noContent();
  }

  @Get(uri = "/{codeSystem}/concepts{?params*}")
  public QueryResult<Concept> getConcepts(@PathVariable String codeSystem, ConceptQueryParams params) {
    params.setCodeSystem(codeSystem);
    return conceptService.query(params);
  }

  @Get(uri = "/{codeSystem}/concepts/{code}")
  public Concept getConcept(@PathVariable String codeSystem, @PathVariable String code) {
    return conceptService.get(codeSystem, code).orElseThrow(() -> new NotFoundException("Concept not found: " + code));
  }

  @Get(uri = "/{codeSystem}/versions/{version}/concepts{?params*}")
  public QueryResult<Concept> getConcepts(@PathVariable String codeSystem, @PathVariable String version, ConceptQueryParams params) {
    params.setCodeSystem(codeSystem);
    params.setCodeSystemVersion(version);
    return conceptService.query(params);
  }

  @Get(uri = "/{codeSystem}/versions/{version}/concepts/{conceptCode}")
  public Concept getConcept(@PathVariable String codeSystem, @PathVariable String version, @PathVariable String conceptCode) {
    return conceptService.get(codeSystem, version, conceptCode).orElseThrow(() -> new NotFoundException("Concept not found: " + conceptCode));
  }

  @Post(uri = "/{codeSystem}/concepts")
  public HttpResponse<?> createConcept(@PathVariable String codeSystem, @Body @Valid Concept concept) {
    concept.setCodeSystem(codeSystem);
    conceptService.save(concept, codeSystem);
    return HttpResponse.created(concept);
  }

  @Get(uri = "/{codeSystem}/properties")
  public List<EntityProperty> getProperties(@PathVariable String codeSystem) {
    return entityPropertyService.getProperties(codeSystem);
  }

  @Post(uri = "/{codeSystem}/properties")
  public HttpResponse<?> createProperties(@PathVariable String codeSystem, @Body EntityPropertyRequest request) {
    return HttpResponse.created(entityPropertyService.save(request.getProperties(), codeSystem));
  }

  @Get(uri = "/{codeSystem}/versions/{version}/entity-versions")
  public List<CodeSystemEntityVersion> getEntityVersions(@PathVariable String codeSystem, @PathVariable String version) {
    CodeSystemEntityVersionQueryParams params = new CodeSystemEntityVersionQueryParams();
    params.setCodeSystem(codeSystem);
    params.setCodeSystemVersion(version);
    return codeSystemEntityVersionService.query(params).getData();
  }

  @Post(uri = "/{codeSystem}/versions/{version}/entity-versions")
  public HttpResponse<?> saveEntityVersions(@PathVariable String codeSystem, @PathVariable String version, @Body EntityVersionRequest request) {
    codeSystemVersionService.saveEntityVersions(codeSystem, version, request.getVersions());
    return HttpResponse.ok();
  }

  @Get(uri = "/{codeSystem}/supplements")
  public List<CodeSystemSupplement> getSupplements(@PathVariable String codeSystem) {
    return codeSystemSupplementService.getSupplements(codeSystem);
  }

  @Post(uri = "/{codeSystem}/supplements")
  public HttpResponse<?> createSupplement(@PathVariable String codeSystem, @Body @Valid CodeSystemSupplement supplement) {
    codeSystemSupplementService.save(supplement, codeSystem);
    return HttpResponse.created(supplement);
  }

  @Put(uri = "/{codeSystem}/supplements/{id}")
  public HttpResponse<?> updateSupplement(@PathVariable String codeSystem, @PathVariable Long id, @Body @Valid CodeSystemSupplement supplement) {
    supplement.setId(id);
    codeSystemSupplementService.save(supplement, codeSystem);
    return HttpResponse.created(supplement);
  }

  @Getter
  @Setter
  private static class EntityPropertyRequest {
    private List<EntityProperty> properties;
  }

  @Getter
  @Setter
  private static class EntityVersionRequest {
    private List<CodeSystemEntityVersion> versions;
  }

}
