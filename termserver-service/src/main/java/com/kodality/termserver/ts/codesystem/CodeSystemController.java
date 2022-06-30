package com.kodality.termserver.ts.codesystem;

import com.kodality.commons.exception.NotFoundException;
import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.auth.auth.Authorized;
import com.kodality.termserver.codesystem.CodeSystem;
import com.kodality.termserver.codesystem.CodeSystemEntityVersion;
import com.kodality.termserver.codesystem.CodeSystemEntityVersionQueryParams;
import com.kodality.termserver.codesystem.CodeSystemQueryParams;
import com.kodality.termserver.codesystem.CodeSystemSupplement;
import com.kodality.termserver.codesystem.CodeSystemVersion;
import com.kodality.termserver.codesystem.Concept;
import com.kodality.termserver.codesystem.ConceptQueryParams;
import com.kodality.termserver.codesystem.Designation;
import com.kodality.termserver.codesystem.EntityProperty;
import com.kodality.termserver.codesystem.EntityPropertyQueryParams;
import com.kodality.termserver.codesystem.EntityPropertyValue;
import com.kodality.termserver.ts.codesystem.concept.ConceptService;
import com.kodality.termserver.ts.codesystem.designation.DesignationService;
import com.kodality.termserver.ts.codesystem.entity.CodeSystemEntityVersionService;
import com.kodality.termserver.ts.codesystem.entityproperty.EntityPropertyService;
import com.kodality.termserver.ts.codesystem.entitypropertyvalue.EntityPropertyValueService;
import com.kodality.termserver.ts.codesystem.supplement.CodeSystemSupplementService;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
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
  private final EntityPropertyValueService entityPropertyValueService;
  private final DesignationService designationService;
  private final CodeSystemVersionService codeSystemVersionService;
  private final CodeSystemDuplicateService codeSystemDuplicateService;
  private final CodeSystemSupplementService codeSystemSupplementService;
  private final CodeSystemEntityVersionService codeSystemEntityVersionService;

  //----------------CodeSystem----------------

  @Authorized("*.code-system.view")
  @Get(uri = "{?params*}")
  public QueryResult<CodeSystem> getCodeSystems(CodeSystemQueryParams params) {
    return codeSystemService.query(params);
  }

  @Authorized("*.code-system.view")
  @Get(uri = "/{codeSystem}")
  public CodeSystem getCodeSystem(@PathVariable String codeSystem) {
    return codeSystemService.get(codeSystem).orElseThrow(() -> new NotFoundException("CodeSystem not found: " + codeSystem));
  }

  @Authorized("*.code-system.edit")
  @Post
  public HttpResponse<?> save(@Body @Valid CodeSystem codeSystem) {
    codeSystemService.save(codeSystem);
    return HttpResponse.created(codeSystem);
  }

  @Authorized("*.code-system.edit")
  @Post(uri = "/{codeSystem}/duplicate")
  public HttpResponse<?> duplicateCodeSystem(@PathVariable String codeSystem, @Body @Valid CodeSystemDuplicateRequest request) {
    CodeSystem targetCodeSystem = new CodeSystem().setId(request.getCodeSystem()).setUri(request.getCodeSystemUri());
    codeSystemDuplicateService.duplicateCodeSystem(targetCodeSystem, codeSystem);
    return HttpResponse.ok();
  }

  //----------------CodeSystem Version----------------

  @Get(uri = "/{codeSystem}/versions")
  @Authorized("*.code-system.view")
  public List<CodeSystemVersion> getCodeSystemVersions(@PathVariable String codeSystem) {
    return codeSystemVersionService.getVersions(codeSystem);
  }

  @Authorized("*.code-system.view")
  @Get(uri = "/{codeSystem}/versions/{version}")
  public CodeSystemVersion getCodeSystemVersion(@PathVariable String codeSystem, @PathVariable String version) {
    return codeSystemVersionService.getVersion(codeSystem, version).orElseThrow(() -> new NotFoundException("CodeSystem version not found: " + codeSystem));
  }

  @Authorized("*.code-system.edit")
  @Post(uri = "/{codeSystem}/versions")
  public HttpResponse<?> createVersion(@PathVariable String codeSystem, @Body @Valid CodeSystemVersion version) {
    version.setId(null);
    version.setCodeSystem(codeSystem);
    codeSystemVersionService.save(version);
    return HttpResponse.created(version);
  }

  @Authorized("*.code-system.edit")
  @Put(uri = "/{codeSystem}/versions/{id}")
  public HttpResponse<?> updateVersion(@PathVariable String codeSystem, @PathVariable Long id, @Body @Valid CodeSystemVersion version) {
    version.setId(id);
    version.setCodeSystem(codeSystem);
    codeSystemVersionService.save(version);
    return HttpResponse.created(version);
  }

  @Authorized("*.code-system.publish")
  @Post(uri = "/{codeSystem}/versions/{version}/activate")
  public HttpResponse<?> activateVersion(@PathVariable String codeSystem, @PathVariable String version) {
    codeSystemVersionService.activate(codeSystem, version);
    return HttpResponse.noContent();
  }

  @Authorized("*.code-system.publish")
  @Post(uri = "/{codeSystem}/versions/{version}/retire")
  public HttpResponse<?> retireVersion(@PathVariable String codeSystem, @PathVariable String version) {
    codeSystemVersionService.retire(codeSystem, version);
    return HttpResponse.noContent();
  }

  @Authorized("*.code-system.edit")
  @Post(uri = "/{codeSystem}/versions/{version}/duplicate")
  public HttpResponse<?> duplicateCodeSystemVersion(@PathVariable String codeSystem, @PathVariable String version,
                                                    @Body @Valid CodeSystemVersionDuplicateRequest request) {
    codeSystemDuplicateService.duplicateCodeSystemVersion(request.getVersion(), request.getCodeSystem(), version, codeSystem);
    return HttpResponse.ok();
  }

  //----------------CodeSystem Concept----------------

  @Authorized("*.code-system.view")
  @Get(uri = "/{codeSystem}/concepts{?params*}")
  public QueryResult<Concept> getConcepts(@PathVariable String codeSystem, ConceptQueryParams params) {
    params.setCodeSystem(codeSystem);
    return conceptService.query(params);
  }

  @Authorized("*.code-system.view")
  @Get(uri = "/{codeSystem}/concepts/{code}")
  public Concept getConcept(@PathVariable String codeSystem, @PathVariable String code) {
    return conceptService.get(codeSystem, code).orElseThrow(() -> new NotFoundException("Concept not found: " + code));
  }

  @Authorized("*.code-system.view")
  @Get(uri = "/{codeSystem}/versions/{version}/concepts{?params*}")
  public QueryResult<Concept> getConcepts(@PathVariable String codeSystem, @PathVariable String version, ConceptQueryParams params) {
    params.setCodeSystem(codeSystem);
    params.setCodeSystemVersion(version);
    return conceptService.query(params);
  }

  @Authorized("*.code-system.view")
  @Get(uri = "/{codeSystem}/versions/{version}/concepts/{conceptCode}")
  public Concept getConcept(@PathVariable String codeSystem, @PathVariable String version, @PathVariable String conceptCode) {
    return conceptService.get(codeSystem, version, conceptCode).orElseThrow(() -> new NotFoundException("Concept not found: " + conceptCode));
  }

  @Authorized("*.code-system.edit")
  @Post(uri = "/{codeSystem}/concepts")
  public HttpResponse<?> createConcept(@PathVariable String codeSystem, @Body @Valid Concept concept) {
    concept.setCodeSystem(codeSystem);
    conceptService.save(concept, codeSystem);
    return HttpResponse.created(concept);
  }

  //----------------CodeSystem Entity Version----------------

  @Authorized("*.code-system.view")
  @Get(uri = "/{codeSystem}/entity-versions{?params*}")
  public QueryResult<CodeSystemEntityVersion> getEntityVersions(@PathVariable String codeSystem, CodeSystemEntityVersionQueryParams params) {
    params.setCodeSystem(codeSystem);
    return codeSystemEntityVersionService.query(params);
  }

  @Authorized("*.code-system.edit")
  @Post(uri = "/{codeSystem}/entities/{entityId}/versions")
  public HttpResponse<?> createEntityVersion(@PathVariable String codeSystem, @PathVariable Long entityId, @Body @Valid CodeSystemEntityVersion version) {
    version.setId(null);
    version.setCodeSystem(codeSystem);
    codeSystemEntityVersionService.save(version, entityId);
    return HttpResponse.created(version);
  }

  @Authorized("*.code-system.edit")
  @Put(uri = "/{codeSystem}/entities/{entityId}/versions/{id}")
  public HttpResponse<?> updateEntityVersion(@PathVariable String codeSystem, @PathVariable Long entityId, @PathVariable Long id,
                                             @Body @Valid CodeSystemEntityVersion version) {
    version.setId(id);
    version.setCodeSystem(codeSystem);
    codeSystemEntityVersionService.save(version, entityId);
    return HttpResponse.created(version);
  }

  @Authorized("*.code-system.publish")
  @Post(uri = "/{codeSystem}/entities/versions/{id}/activate")
  public HttpResponse<?> activateEntityVersion(@PathVariable String codeSystem, @PathVariable Long id) {
    codeSystemEntityVersionService.activate(id);
    return HttpResponse.noContent();
  }

  @Authorized("*.code-system.publish")
  @Post(uri = "/{codeSystem}/entities/versions/{id}/retire")
  public HttpResponse<?> retireEntityVersion(@PathVariable String codeSystem, @PathVariable Long id) {
    codeSystemEntityVersionService.retire(id);
    return HttpResponse.noContent();
  }

  @Authorized("*.code-system.edit")
  @Post(uri = "/{codeSystem}/entities/versions/{id}/supplements")
  public HttpResponse<?> createSupplement(@PathVariable String codeSystem, @PathVariable Long id, @Body @Valid CodeSystemSupplement supplement) {
    codeSystemSupplementService.save(supplement, id);
    return HttpResponse.created(supplement);
  }

  @Authorized("*.code-system.edit")
  @Put(uri = "/{codeSystem}/entities/versions/{entityVersionId}/supplements/{id}")
  public HttpResponse<?> createSupplement(@PathVariable String codeSystem, @PathVariable Long entityVersionId, @PathVariable Long id,@Body @Valid CodeSystemSupplement supplement) {
    supplement.setId(id);
    codeSystemSupplementService.save(supplement, entityVersionId);
    return HttpResponse.created(supplement);
  }

  @Authorized("*.code-system.edit")
  @Post(uri = "/{codeSystem}/versions/{version}/entity-versions/{entityVersionId}/membership")
  public HttpResponse<?> linkEntityVersion(@PathVariable String codeSystem, @PathVariable String version, @PathVariable Long entityVersionId) {
    codeSystemVersionService.linkEntityVersion(codeSystem, version, entityVersionId);
    return HttpResponse.ok();
  }

  @Authorized("*.code-system.edit")
  @Delete(uri = "/{codeSystem}/versions/{version}/entity-versions/{entityVersionId}/membership")
  public HttpResponse<?> unlinkEntityVersion(@PathVariable String codeSystem, @PathVariable String version, @PathVariable Long entityVersionId) {
    codeSystemVersionService.unlinkEntityVersion(codeSystem, version, entityVersionId);
    return HttpResponse.ok();
  }

  //----------------CodeSystem Property----------------

  @Authorized("*.code-system.view")
  @Get(uri = "/{codeSystem}/entity-properties/{id}")
  public EntityProperty getEntityProperty(@PathVariable String codeSystem, @PathVariable Long id) {
    return entityPropertyService.getProperty(id);
  }

  @Authorized("*.code-system.view")
  @Get(uri = "/{codeSystem}/entity-properties{?params*}")
  public QueryResult<EntityProperty> queryEntityProperties(@PathVariable String codeSystem, EntityPropertyQueryParams params) {
    params.setCodeSystem(codeSystem);
    return entityPropertyService.query(params);
  }

  @Authorized("*.code-system.edit")
  @Post(uri = "/{codeSystem}/entity-properties")
  public HttpResponse<?> createEntityProperty(@PathVariable String codeSystem, @Body @Valid EntityProperty property) {
    entityPropertyService.save(property, codeSystem);
    return HttpResponse.created(property);
  }

  @Authorized("*.code-system.edit")
  @Put(uri = "/{codeSystem}/entity-properties/{id}")
  public HttpResponse<?> updateEntityProperty(@PathVariable String codeSystem, @PathVariable Long id, @Body @Valid EntityProperty property) {
    property.setId(id);
    entityPropertyService.save(property, codeSystem);
    return HttpResponse.created(property);
  }

  @Authorized("*.code-system.edit")
  @Delete(uri = "/{codeSystem}/entity-properties/{id}")
  public HttpResponse<?> deleteEntityProperty(@PathVariable String codeSystem, @PathVariable Long id) {
    entityPropertyService.delete(id);
    return HttpResponse.ok();
  }

  //----------------CodeSystem Property Value----------------

  @Authorized("*.code-system.view")
  @Get(uri = "/{codeSystem}/entity-property-values/{id}")
  public EntityPropertyValue getEntityPropertyValue(@PathVariable String codeSystem, @PathVariable Long id) {
    return entityPropertyValueService.load(id);
  }

  @Authorized("*.code-system.edit")
  @Post(uri = "/{codeSystem}/entity-versions/{entityVersionId}/entity-property-values")
  public HttpResponse<?> createEntityPropertyValue(@PathVariable String codeSystem, @PathVariable Long entityVersionId, @Body @Valid EntityPropertyValue propertyValue) {
    entityPropertyValueService.save(propertyValue, entityVersionId);
    return HttpResponse.created(propertyValue);
  }

  @Authorized("*.code-system.edit")
  @Put(uri = "/{codeSystem}/entity-versions/{entityVersionId}/entity-property-values/{id}")
  public HttpResponse<?> updateEntityPropertyValue(@PathVariable String codeSystem, @PathVariable Long entityVersionId, @PathVariable Long id, @Body @Valid EntityPropertyValue propertyValue) {
    propertyValue.setId(id);
    entityPropertyValueService.save(propertyValue, entityVersionId);
    return HttpResponse.created(propertyValue);
  }

  @Authorized("*.code-system.edit")
  @Delete(uri = "/{codeSystem}/entity-property-values/{id}")
  public HttpResponse<?> deleteEntityPropertyValue(@PathVariable String codeSystem, @PathVariable Long id) {
    entityPropertyValueService.delete(id);
    return HttpResponse.ok();
  }

  //----------------CodeSystem Designation---------------

  @Authorized("*.code-system.view")
  @Get(uri = "/{codeSystem}/designations/{id}")
  public Designation getDesignation(@PathVariable String codeSystem, @PathVariable Long id) {
    return designationService.get(id).orElseThrow(() -> new NotFoundException("Designation not found: " + id));
  }

  @Authorized("*.code-system.edit")
  @Post(uri = "/{codeSystem}/entity-versions/{entityVersionId}/designations")
  public HttpResponse<?> createDesignation(@PathVariable String codeSystem, @PathVariable Long entityVersionId, @Body @Valid Designation designation) {
    designationService.save(designation, entityVersionId);
    return HttpResponse.created(designation);
  }
  @Authorized("*.code-system.edit")
  @Put(uri = "/{codeSystem}/entity-versions/{entityVersionId}/designations/{id}")
  public HttpResponse<?> updateDesignation(@PathVariable String codeSystem, @PathVariable Long entityVersionId, @PathVariable Long id, @Body @Valid Designation designation) {
    designation.setId(id);
    designationService.save(designation, entityVersionId);
    return HttpResponse.created(designation);
  }

  @Authorized("*.code-system.edit")
  @Delete(uri = "/{codeSystem}/designations/{id}")
  public HttpResponse<?> deleteDesignation(@PathVariable String codeSystem, @PathVariable Long id) {
    designationService.delete(id);
    return HttpResponse.ok();
  }

  //----------------CodeSystem Supplement----------------

  @Authorized("*.code-system.view")
  @Get(uri = "/{codeSystem}/supplements")
  public List<CodeSystemSupplement> getSupplements(@PathVariable String codeSystem) {
    return codeSystemSupplementService.getSupplements(codeSystem);
  }

  @Authorized("*.code-system.view")
  @Get(uri = "/{codeSystem}/supplements/{id}")
  public CodeSystemSupplement getSupplement(@PathVariable String codeSystem, @PathVariable Long id) {
    return codeSystemSupplementService.getSupplement(id);
  }

  @Authorized("*.code-system.edit")
  @Post(uri = "/{codeSystem}/supplements")
  public HttpResponse<?> createSupplement(@PathVariable String codeSystem, @Body @Valid CodeSystemSupplement supplement) {
    codeSystemSupplementService.save(supplement, codeSystem);
    return HttpResponse.created(supplement);
  }

  @Authorized("*.code-system.edit")
  @Put(uri = "/{codeSystem}/supplements/{id}")
  public HttpResponse<?> updateSupplement(@PathVariable String codeSystem, @PathVariable Long id, @Body @Valid CodeSystemSupplement supplement) {
    supplement.setId(id);
    codeSystemSupplementService.save(supplement, codeSystem);
    return HttpResponse.created(supplement);
  }

  @Getter
  @Setter
  private static class CodeSystemDuplicateRequest {
    private String codeSystem;
    private String codeSystemUri;
  }

  @Getter
  @Setter
  private static class CodeSystemVersionDuplicateRequest {
    private String codeSystem;
    private String version;
  }
}
