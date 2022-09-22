package com.kodality.termserver.ts.codesystem;

import com.kodality.commons.exception.NotFoundException;
import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.auth.auth.Authorized;
import com.kodality.termserver.auth.auth.ResourceId;
import com.kodality.termserver.auth.auth.UserPermissionService;
import com.kodality.termserver.codesystem.CodeSystem;
import com.kodality.termserver.codesystem.CodeSystemAssociation;
import com.kodality.termserver.codesystem.CodeSystemEntityVersion;
import com.kodality.termserver.codesystem.CodeSystemEntityVersionQueryParams;
import com.kodality.termserver.codesystem.CodeSystemQueryParams;
import com.kodality.termserver.codesystem.CodeSystemSupplement;
import com.kodality.termserver.codesystem.CodeSystemVersion;
import com.kodality.termserver.codesystem.CodeSystemVersionQueryParams;
import com.kodality.termserver.codesystem.Concept;
import com.kodality.termserver.codesystem.ConceptQueryParams;
import com.kodality.termserver.codesystem.Designation;
import com.kodality.termserver.codesystem.EntityProperty;
import com.kodality.termserver.codesystem.EntityPropertyQueryParams;
import com.kodality.termserver.codesystem.EntityPropertyValue;
import com.kodality.termserver.ts.codesystem.association.CodeSystemAssociationService;
import com.kodality.termserver.ts.codesystem.concept.ConceptService;
import com.kodality.termserver.ts.codesystem.designation.DesignationService;
import com.kodality.termserver.ts.codesystem.entity.CodeSystemEntityVersionService;
import com.kodality.termserver.ts.codesystem.entityproperty.EntityPropertyService;
import com.kodality.termserver.ts.codesystem.entitypropertyvalue.EntityPropertyValueService;
import com.kodality.termserver.ts.codesystem.supplement.CodeSystemSupplementService;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.annotation.QueryValue;
import java.util.List;
import java.util.Optional;
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
  private final CodeSystemDeleteService codeSystemDeleteService;
  private final CodeSystemAssociationService associationService;
  private final CodeSystemVersionService codeSystemVersionService;
  private final CodeSystemDuplicateService codeSystemDuplicateService;
  private final CodeSystemSupplementService codeSystemSupplementService;
  private final CodeSystemEntityVersionService codeSystemEntityVersionService;

  private final UserPermissionService userPermissionService;

  //----------------CodeSystem----------------

  @Authorized("*.CodeSystem.view")
  @Get(uri = "{?params*}")
  public QueryResult<CodeSystem> queryCodeSystems(CodeSystemQueryParams params) {
    params.setPermittedIds(userPermissionService.getPermittedResourceIds("CodeSystem", "view"));
    return codeSystemService.query(params);
  }

  @Authorized("*.CodeSystem.view")
  @Get(uri = "/{codeSystem}{?decorate}")
  public CodeSystem getCodeSystem(@PathVariable @ResourceId String codeSystem, Optional<Boolean> decorate) {
    return codeSystemService.load(codeSystem, decorate.orElse(false)).orElseThrow(() -> new NotFoundException("CodeSystem not found: " + codeSystem));
  }

  @Authorized("*.CodeSystem.edit")
  @Post
  public HttpResponse<?> saveCodeSystem(@Body @Valid CodeSystem codeSystem) {
    codeSystemService.save(codeSystem);
    return HttpResponse.created(codeSystem);
  }

  @Authorized("*.CodeSystem.edit")
  @Post(uri = "/{codeSystem}/duplicate")
  public HttpResponse<?> duplicateCodeSystem(@PathVariable String codeSystem, @Body @Valid CodeSystemDuplicateRequest request) {
    CodeSystem targetCodeSystem = new CodeSystem().setId(request.getCodeSystem()).setUri(request.getCodeSystemUri());
    codeSystemDuplicateService.duplicateCodeSystem(targetCodeSystem, codeSystem);
    return HttpResponse.ok();
  }

  @Authorized("*.CodeSystem.publish")
  @Delete(uri = "/{codeSystem}")
  public HttpResponse<?> deleteCodeSystem(@PathVariable @ResourceId String codeSystem) {
    codeSystemDeleteService.deleteCodeSystem(codeSystem);
    return HttpResponse.ok();
  }

  //----------------CodeSystem Version----------------

  @Authorized("*.CodeSystem.view")
  @Get(uri = "/{codeSystem}/versions{?params*}")
  public QueryResult<CodeSystemVersion> queryCodeSystemVersions(@PathVariable @ResourceId String codeSystem, CodeSystemVersionQueryParams params) {
    params.setPermittedCodeSystems(userPermissionService.getPermittedResourceIds("CodeSystem", "view"));
    params.setCodeSystem(codeSystem);
    return codeSystemVersionService.query(params);
  }

  @Authorized("*.CodeSystem.view")
  @Get(uri = "/{codeSystem}/versions/{version}")
  public CodeSystemVersion getCodeSystemVersion(@PathVariable @ResourceId String codeSystem, @PathVariable String version) {
    return codeSystemVersionService.load(codeSystem, version).orElseThrow(() -> new NotFoundException("CodeSystemVersion not found: " + codeSystem));
  }

  @Authorized("*.CodeSystem.edit")
  @Post(uri = "/{codeSystem}/versions")
  public HttpResponse<?> createCodeSystemVersion(@PathVariable @ResourceId String codeSystem, @Body @Valid CodeSystemVersion version) {
    version.setId(null);
    version.setCodeSystem(codeSystem);
    codeSystemVersionService.save(version);
    return HttpResponse.created(version);
  }

  @Authorized("*.CodeSystem.edit")
  @Put(uri = "/{codeSystem}/versions/{id}")
  public HttpResponse<?> updateCodeSystemVersion(@PathVariable @ResourceId String codeSystem, @PathVariable Long id, @Body @Valid CodeSystemVersion version) {
    version.setId(id);
    version.setCodeSystem(codeSystem);
    codeSystemVersionService.save(version);
    return HttpResponse.created(version);
  }

  @Authorized("*.CodeSystem.publish")
  @Post(uri = "/{codeSystem}/versions/{version}/activate")
  public HttpResponse<?> activateCodeSystemVersion(@PathVariable @ResourceId String codeSystem, @PathVariable String version) {
    codeSystemVersionService.activate(codeSystem, version);
    return HttpResponse.noContent();
  }

  @Authorized("*.CodeSystem.publish")
  @Post(uri = "/{codeSystem}/versions/{version}/retire")
  public HttpResponse<?> retireCodeSystemVersion(@PathVariable @ResourceId String codeSystem, @PathVariable String version) {
    codeSystemVersionService.retire(codeSystem, version);
    return HttpResponse.noContent();
  }

  @Authorized("*.CodeSystem.edit")
  @Post(uri = "/{codeSystem}/versions/{version}/duplicate")
  public HttpResponse<?> duplicateCodeSystemVersion(@PathVariable String codeSystem, @PathVariable String version, @Body @Valid CodeSystemVersionDuplicateRequest request) {
    codeSystemDuplicateService.duplicateCodeSystemVersion(request.getVersion(), request.getCodeSystem(), version, codeSystem);
    return HttpResponse.ok();
  }

  //----------------CodeSystem Concept----------------

  @Authorized("*.CodeSystem.view")
  @Get(uri = "/{codeSystem}/concepts{?params*}")
  public QueryResult<Concept> queryConcepts(@PathVariable @ResourceId String codeSystem, ConceptQueryParams params) {
    params.setPermittedCodeSystems(userPermissionService.getPermittedResourceIds("CodeSystem", "view"));
    params.setCodeSystem(codeSystem);
    return conceptService.query(params);
  }

  @Authorized("*.CodeSystem.view")
  @Get(uri = "/{codeSystem}/concepts/{code}")
  public Concept getConcept(@PathVariable @ResourceId String codeSystem, @PathVariable String code) {
    return conceptService.load(codeSystem, code).orElseThrow(() -> new NotFoundException("Concept not found: " + code));
  }

  @Authorized("*.CodeSystem.view")
  @Get(uri = "/{codeSystem}/versions/{version}/concepts/{code}")
  public Concept getConcept(@PathVariable @ResourceId String codeSystem, @PathVariable String version, @PathVariable String code) {
    return conceptService.load(codeSystem, version, code).orElseThrow(() -> new NotFoundException("Concept not found: " + code));
  }

  @Authorized("*.CodeSystem.edit")
  @Post(uri = "/{codeSystem}/concepts")
  public HttpResponse<?> createConcept(@PathVariable @ResourceId String codeSystem, @QueryValue Optional<Boolean> full, @Body @Valid Concept concept) {
    concept.setId(null);
    concept.setCodeSystem(codeSystem);
    if (full.orElse(false)) {
      conceptService.saveWithVersions(concept, codeSystem);
    } else {
      conceptService.save(concept, codeSystem);
    }
    return HttpResponse.created(concept);
  }

  @Authorized("*.CodeSystem.edit")
  @Put(uri = "/{codeSystem}/concepts/{id}")
  public HttpResponse<?> updateConcept(@PathVariable @ResourceId String codeSystem, @PathVariable Long id, @QueryValue Optional<Boolean> full, @Body @Valid Concept concept) {
    concept.setId(id);
    concept.setCodeSystem(codeSystem);
    if (full.orElse(false)) {
      conceptService.saveWithVersions(concept, codeSystem);
    } else {
      conceptService.save(concept, codeSystem);
    }
    return HttpResponse.created(concept);
  }

  //----------------CodeSystem EntityVersion----------------

  @Authorized("*.CodeSystem.view")
  @Get(uri = "/{codeSystem}/entity-versions{?params*}")
  public QueryResult<CodeSystemEntityVersion> queryEntityVersions(@PathVariable @ResourceId String codeSystem, CodeSystemEntityVersionQueryParams params) {
    params.setPermittedCodeSystems(userPermissionService.getPermittedResourceIds("CodeSystem", "view"));
    params.setCodeSystem(codeSystem);
    return codeSystemEntityVersionService.query(params);
  }

  @Authorized("*.CodeSystem.edit")
  @Post(uri = "/{codeSystem}/entities/{entityId}/versions")
  public HttpResponse<?> createEntityVersion(@PathVariable @ResourceId String codeSystem, @PathVariable Long entityId, @Body @Valid CodeSystemEntityVersion version) {
    version.setId(null);
    version.setCodeSystem(codeSystem);
    codeSystemEntityVersionService.save(version, entityId);
    return HttpResponse.created(version);
  }

  @Authorized("*.CodeSystem.edit")
  @Put(uri = "/{codeSystem}/entities/{entityId}/versions/{id}")
  public HttpResponse<?> updateEntityVersion(@PathVariable @ResourceId String codeSystem, @PathVariable Long entityId, @PathVariable Long id, @Body @Valid CodeSystemEntityVersion version) {
    version.setId(id);
    version.setCodeSystem(codeSystem);
    codeSystemEntityVersionService.save(version, entityId);
    return HttpResponse.created(version);
  }

  @Authorized("*.CodeSystem.publish")
  @Post(uri = "/{codeSystem}/entities/versions/{id}/activate")
  public HttpResponse<?> activateEntityVersion(@PathVariable @ResourceId String codeSystem, @PathVariable Long id) {
    codeSystemEntityVersionService.activate(id);
    return HttpResponse.noContent();
  }

  @Authorized("*.CodeSystem.publish")
  @Post(uri = "/{codeSystem}/entities/versions/{id}/retire")
  public HttpResponse<?> retireEntityVersion(@PathVariable @ResourceId String codeSystem, @PathVariable Long id) {
    codeSystemEntityVersionService.retire(id);
    return HttpResponse.noContent();
  }

  @Authorized("*.CodeSystem.edit")
  @Post(uri = "/{codeSystem}/entities/{entityId}/versions/{id}/duplicate")
  public HttpResponse<?> duplicateEntityVersion(@PathVariable String codeSystem,  @PathVariable Long entityId, @PathVariable Long id) {
    codeSystemEntityVersionService.duplicate(codeSystem, entityId, id);
    return HttpResponse.ok();
  }

  @Authorized("*.CodeSystem.edit")
  @Post(uri = "/{codeSystem}/versions/{version}/entity-versions/{entityVersionId}/membership")
  public HttpResponse<?> linkEntityVersion(@PathVariable @ResourceId String codeSystem, @PathVariable String version, @PathVariable Long entityVersionId) {
    codeSystemVersionService.linkEntityVersion(codeSystem, version, entityVersionId);
    return HttpResponse.ok();
  }

  @Authorized("*.CodeSystem.edit")
  @Delete(uri = "/{codeSystem}/versions/{version}/entity-versions/{entityVersionId}/membership")
  public HttpResponse<?> unlinkEntityVersion(@PathVariable @ResourceId String codeSystem, @PathVariable String version, @PathVariable Long entityVersionId) {
    codeSystemVersionService.unlinkEntityVersion(codeSystem, version, entityVersionId);
    return HttpResponse.ok();
  }

  //----------------CodeSystem Property----------------

  @Authorized("*.CodeSystem.view")
  @Get(uri = "/{codeSystem}/entity-properties{?params*}")
  public QueryResult<EntityProperty> queryEntityProperties(@PathVariable @ResourceId String codeSystem, EntityPropertyQueryParams params) {
    params.setPermittedCodeSystems(userPermissionService.getPermittedResourceIds("CodeSystem", "view"));
    params.setCodeSystem(codeSystem);
    return entityPropertyService.query(params);
  }

  @Authorized("*.CodeSystem.view")
  @Get(uri = "/{codeSystem}/entity-properties/{id}")
  public EntityProperty getEntityProperty(@PathVariable @ResourceId String codeSystem, @PathVariable Long id) {
    return entityPropertyService.load(id).orElseThrow(() -> new NotFoundException("EntityProperty not found: " + id));
  }

  @Authorized("*.CodeSystem.edit")
  @Post(uri = "/{codeSystem}/entity-properties")
  public HttpResponse<?> createEntityProperty(@PathVariable @ResourceId String codeSystem, @Body @Valid EntityProperty property) {
    property.setId(null);
    entityPropertyService.save(property, codeSystem);
    return HttpResponse.created(property);
  }

  @Authorized("*.CodeSystem.edit")
  @Put(uri = "/{codeSystem}/entity-properties/{id}")
  public HttpResponse<?> updateEntityProperty(@PathVariable @ResourceId String codeSystem, @PathVariable Long id, @Body @Valid EntityProperty property) {
    property.setId(id);
    entityPropertyService.save(property, codeSystem);
    return HttpResponse.created(property);
  }

  @Authorized("*.CodeSystem.edit")
  @Delete(uri = "/{codeSystem}/entity-properties/{id}")
  public HttpResponse<?> deleteEntityProperty(@PathVariable @ResourceId String codeSystem, @PathVariable Long id) {
    entityPropertyService.cancel(id, codeSystem);
    return HttpResponse.ok();
  }

  //----------------CodeSystem PropertyValue----------------

  @Authorized("*.CodeSystem.view")
  @Get(uri = "/{codeSystem}/entity-property-values/{id}")
  public EntityPropertyValue getEntityPropertyValue(@PathVariable @ResourceId String codeSystem, @PathVariable Long id) {
    return entityPropertyValueService.load(id).orElseThrow(() -> new NotFoundException("EntityPropertyValue not found: " + id));
  }

  @Authorized("*.CodeSystem.edit")
  @Post(uri = "/{codeSystem}/entity-versions/{entityVersionId}/entity-property-values")
  public HttpResponse<?> createEntityPropertyValue(@PathVariable @ResourceId String codeSystem, @PathVariable Long entityVersionId, @Body @Valid EntityPropertyValue propertyValue) {
    propertyValue.setId(null);
    entityPropertyValueService.save(propertyValue, entityVersionId, codeSystem);
    return HttpResponse.created(propertyValue);
  }

  @Authorized("*.CodeSystem.edit")
  @Put(uri = "/{codeSystem}/entity-versions/{entityVersionId}/entity-property-values/{id}")
  public HttpResponse<?> updateEntityPropertyValue(@PathVariable @ResourceId String codeSystem, @PathVariable Long entityVersionId, @PathVariable Long id, @Body @Valid EntityPropertyValue propertyValue) {
    propertyValue.setId(id);
    entityPropertyValueService.save(propertyValue, entityVersionId, codeSystem);
    return HttpResponse.created(propertyValue);
  }

  @Authorized("*.CodeSystem.edit")
  @Delete(uri = "/{codeSystem}/entity-property-values/{id}")
  public HttpResponse<?> deleteEntityPropertyValue(@PathVariable @ResourceId String codeSystem, @PathVariable Long id) {
    entityPropertyValueService.delete(id, codeSystem);
    return HttpResponse.ok();
  }

  //----------------CodeSystem Designation---------------

  @Authorized("*.CodeSystem.view")
  @Get(uri = "/{codeSystem}/designations/{id}")
  public Designation getDesignation(@PathVariable @ResourceId String codeSystem, @PathVariable Long id) {
    return designationService.load(id).orElseThrow(() -> new NotFoundException("Designation not found: " + id));
  }

  @Authorized("*.CodeSystem.edit")
  @Post(uri = "/{codeSystem}/entity-versions/{entityVersionId}/designations")
  public HttpResponse<?> createDesignation(@PathVariable @ResourceId String codeSystem, @PathVariable Long entityVersionId, @Body @Valid Designation designation) {
    designation.setId(null);
    designationService.save(designation, entityVersionId, codeSystem);
    return HttpResponse.created(designation);
  }

  @Authorized("*.CodeSystem.edit")
  @Put(uri = "/{codeSystem}/entity-versions/{entityVersionId}/designations/{id}")
  public HttpResponse<?> updateDesignation(@PathVariable @ResourceId String codeSystem, @PathVariable Long entityVersionId, @PathVariable Long id, @Body @Valid Designation designation) {
    designation.setId(id);
    designationService.save(designation, entityVersionId, codeSystem);
    return HttpResponse.created(designation);
  }

  @Authorized("*.CodeSystem.edit")
  @Delete(uri = "/{codeSystem}/designations/{id}")
  public HttpResponse<?> deleteDesignation(@PathVariable @ResourceId String codeSystem, @PathVariable Long id) {
    designationService.delete(id, codeSystem);
    return HttpResponse.ok();
  }

  //----------------CodeSystem Association---------------

  @Authorized("*.CodeSystem.view")
  @Get(uri = "/{codeSystem}/associations/{id}")
  public CodeSystemAssociation getAssociation(@PathVariable String codeSystem, @PathVariable Long id) {
    return associationService.load(id).orElseThrow(() -> new NotFoundException("Association not found: " + id));
  }

  @Authorized("*.CodeSystem.edit")
  @Post(uri = "/{codeSystem}/entity-versions/{entityVersionId}/associations")
  public HttpResponse<?> createAssociation(@PathVariable @ResourceId String codeSystem, @PathVariable Long entityVersionId, @Body @Valid CodeSystemAssociation association) {
    association.setId(null);
    associationService.save(association, entityVersionId, codeSystem);
    return HttpResponse.created(association);
  }

  @Authorized("*.CodeSystem.edit")
  @Put(uri = "/{codeSystem}/entity-versions/{entityVersionId}/associations/{id}")
  public HttpResponse<?> updateAssociation(@PathVariable @ResourceId String codeSystem, @PathVariable Long entityVersionId, @PathVariable Long id, @Body @Valid CodeSystemAssociation association) {
    association.setId(id);
    associationService.save(association, entityVersionId, codeSystem);
    return HttpResponse.created(association);
  }

  @Authorized("*.CodeSystem.edit")
  @Delete(uri = "/{codeSystem}/associations/{id}")
  public HttpResponse<?> deleteAssociation(@PathVariable @ResourceId String codeSystem, @PathVariable Long id) {
    associationService.delete(id, codeSystem);
    return HttpResponse.ok();
  }

  //----------------CodeSystem Supplement----------------

  @Authorized("*.CodeSystem.view")
  @Get(uri = "/{codeSystem}/supplements")
  public List<CodeSystemSupplement> getSupplements(@PathVariable @ResourceId String codeSystem) {
    return codeSystemSupplementService.getSupplements(codeSystem);
  }

  @Authorized("*.CodeSystem.view")
  @Get(uri = "/{codeSystem}/supplements/{id}")
  public CodeSystemSupplement getSupplement(@PathVariable @ResourceId String codeSystem, @PathVariable Long id) {
    return codeSystemSupplementService.load(id).orElseThrow(() -> new NotFoundException("Supplement not found: " + id));
  }

  @Authorized("*.CodeSystem.edit")
  @Post(uri = "/{codeSystem}/supplements")
  public HttpResponse<?> createSupplement(@PathVariable @ResourceId String codeSystem, @Body @Valid CodeSystemSupplement supplement) {
    codeSystemSupplementService.save(supplement, codeSystem);
    return HttpResponse.created(supplement);
  }

  @Authorized("*.CodeSystem.edit")
  @Put(uri = "/{codeSystem}/supplements/{id}")
  public HttpResponse<?> updateSupplement(@PathVariable @ResourceId String codeSystem, @PathVariable Long id, @Body @Valid CodeSystemSupplement supplement) {
    supplement.setId(id);
    codeSystemSupplementService.save(supplement, codeSystem);
    return HttpResponse.created(supplement);
  }

  @Authorized("*.CodeSystem.edit")
  @Post(uri = "/{codeSystem}/entities/versions/{entityVersionId}/supplements")
  public HttpResponse<?> createSupplement(@PathVariable @ResourceId String codeSystem, @PathVariable Long entityVersionId, @Body @Valid CodeSystemSupplement supplement) {
    codeSystemSupplementService.save(supplement, entityVersionId, codeSystem);
    return HttpResponse.created(supplement);
  }

  @Authorized("*.CodeSystem.edit")
  @Put(uri = "/{codeSystem}/entities/versions/{entityVersionId}/supplements/{id}")
  public HttpResponse<?> createSupplement(@PathVariable String codeSystem, @PathVariable Long entityVersionId, @PathVariable Long id, @Body @Valid CodeSystemSupplement supplement) {
    supplement.setId(id);
    codeSystemSupplementService.save(supplement, entityVersionId, codeSystem);
    return HttpResponse.created(supplement);
  }

  @Getter
  @Setter
  @Introspected
  private static class CodeSystemDuplicateRequest {
    private String codeSystem;
    private String codeSystemUri;
  }

  @Getter
  @Setter
  @Introspected
  private static class CodeSystemVersionDuplicateRequest {
    private String codeSystem;
    private String version;
  }
}
