package com.kodality.termserver.terminology.codesystem;

import com.kodality.commons.exception.NotFoundException;
import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.Privilege;
import com.kodality.termserver.auth.Authorized;
import com.kodality.termserver.auth.ResourceId;
import com.kodality.termserver.auth.UserPermissionService;
import com.kodality.termserver.sys.provenance.Provenance;
import com.kodality.termserver.sys.provenance.ProvenanceService;
import com.kodality.termserver.terminology.codesystem.association.CodeSystemAssociationService;
import com.kodality.termserver.terminology.codesystem.concept.ConceptService;
import com.kodality.termserver.terminology.codesystem.designation.DesignationService;
import com.kodality.termserver.terminology.codesystem.entity.CodeSystemEntityVersionService;
import com.kodality.termserver.terminology.codesystem.entityproperty.EntityPropertyService;
import com.kodality.termserver.terminology.codesystem.entitypropertyvalue.EntityPropertyValueService;
import com.kodality.termserver.terminology.codesystem.supplement.CodeSystemSupplementService;
import com.kodality.termserver.ts.codesystem.CodeSystem;
import com.kodality.termserver.ts.codesystem.CodeSystemAssociation;
import com.kodality.termserver.ts.codesystem.CodeSystemEntityVersion;
import com.kodality.termserver.ts.codesystem.CodeSystemEntityVersionQueryParams;
import com.kodality.termserver.ts.codesystem.CodeSystemQueryParams;
import com.kodality.termserver.ts.codesystem.CodeSystemSupplement;
import com.kodality.termserver.ts.codesystem.CodeSystemTransactionRequest;
import com.kodality.termserver.ts.codesystem.CodeSystemVersion;
import com.kodality.termserver.ts.codesystem.CodeSystemVersionQueryParams;
import com.kodality.termserver.ts.codesystem.Concept;
import com.kodality.termserver.ts.codesystem.ConceptQueryParams;
import com.kodality.termserver.ts.codesystem.Designation;
import com.kodality.termserver.ts.codesystem.EntityProperty;
import com.kodality.termserver.ts.codesystem.EntityPropertyQueryParams;
import com.kodality.termserver.ts.codesystem.EntityPropertyValue;
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
  private final CodeSystemAssociationService associationService;
  private final CodeSystemVersionService codeSystemVersionService;
  private final CodeSystemDuplicateService codeSystemDuplicateService;
  private final CodeSystemSupplementService codeSystemSupplementService;
  private final CodeSystemEntityVersionService codeSystemEntityVersionService;
  private final ProvenanceService provenanceService;

  private final UserPermissionService userPermissionService;

  //----------------CodeSystem----------------

  @Authorized(Privilege.CS_VIEW)
  @Get(uri = "{?params*}")
  public QueryResult<CodeSystem> queryCodeSystems(CodeSystemQueryParams params) {
    params.setPermittedIds(userPermissionService.getPermittedResourceIds("CodeSystem", "view"));
    return codeSystemService.query(params);
  }

  @Authorized(Privilege.CS_VIEW)
  @Get(uri = "/{codeSystem}{?decorate}")
  public CodeSystem getCodeSystem(@PathVariable @ResourceId String codeSystem, Optional<Boolean> decorate) {
    return codeSystemService.load(codeSystem, decorate.orElse(false)).orElseThrow(() -> new NotFoundException("CodeSystem not found: " + codeSystem));
  }

  @Authorized(Privilege.CS_EDIT)
  @Post
  public HttpResponse<?> saveCodeSystem(@Body @Valid CodeSystem codeSystem) {
    codeSystemService.save(codeSystem);
    provenanceService.create(new Provenance("created", "CodeSystem", codeSystem.getId()));
    return HttpResponse.created(codeSystem);
  }

  @Authorized(Privilege.CS_EDIT)
  @Post("/transaction")
  public HttpResponse<?> saveCodeSystemTransaction(@Body @Valid CodeSystemTransactionRequest codeSystem) {
    codeSystemService.save(codeSystem);
    provenanceService.create(new Provenance("modified", "CodeSystem", codeSystem.getCodeSystem().getId()));
    return HttpResponse.created(codeSystem);
  }

  @Authorized(Privilege.CS_EDIT)
  @Post(uri = "/{codeSystem}/duplicate")
  public HttpResponse<?> duplicateCodeSystem(@PathVariable String codeSystem, @Body @Valid CodeSystemDuplicateRequest request) {
    CodeSystem targetCodeSystem = new CodeSystem().setId(request.getCodeSystem()).setUri(request.getCodeSystemUri());
    codeSystemDuplicateService.duplicateCodeSystem(targetCodeSystem, codeSystem);
    provenanceService.create(new Provenance("created", "CodeSystem", targetCodeSystem.getId()));
    return HttpResponse.ok();
  }

  @Authorized(Privilege.CS_PUBLISH)
  @Delete(uri = "/{codeSystem}")
  public HttpResponse<?> deleteCodeSystem(@PathVariable @ResourceId String codeSystem) {
    codeSystemService.cancel(codeSystem);
    provenanceService.create(new Provenance("deleted", "CodeSystem", codeSystem));
    return HttpResponse.ok();
  }

  //----------------CodeSystem Version----------------

  @Authorized(Privilege.CS_VIEW)
  @Get(uri = "/{codeSystem}/versions{?params*}")
  public QueryResult<CodeSystemVersion> queryCodeSystemVersions(@PathVariable @ResourceId String codeSystem, CodeSystemVersionQueryParams params) {
    params.setPermittedCodeSystems(userPermissionService.getPermittedResourceIds("CodeSystem", "view"));
    params.setCodeSystem(codeSystem);
    return codeSystemVersionService.query(params);
  }

  @Authorized(Privilege.CS_VIEW)
  @Get(uri = "/{codeSystem}/versions/{version}")
  public CodeSystemVersion getCodeSystemVersion(@PathVariable @ResourceId String codeSystem, @PathVariable String version) {
    return codeSystemVersionService.load(codeSystem, version).orElseThrow(() -> new NotFoundException("CodeSystemVersion not found: " + codeSystem));
  }

  @Authorized(Privilege.CS_EDIT)
  @Post(uri = "/{codeSystem}/versions")
  public HttpResponse<?> createCodeSystemVersion(@PathVariable @ResourceId String codeSystem, @Body @Valid CodeSystemVersion version) {
    version.setId(null);
    version.setCodeSystem(codeSystem);
    codeSystemVersionService.save(version);
    provenanceService.create(new Provenance("created", "CodeSystemVersion", version.getId().toString())
        .addContext("part-of", "CodeSystem", version.getCodeSystem()));
    return HttpResponse.created(version);
  }

  @Authorized(Privilege.CS_EDIT)
  @Put(uri = "/{codeSystem}/versions/{id}")
  public HttpResponse<?> updateCodeSystemVersion(@PathVariable @ResourceId String codeSystem, @PathVariable Long id, @Body @Valid CodeSystemVersion version) {
    version.setId(id);
    version.setCodeSystem(codeSystem);
    codeSystemVersionService.save(version);
    provenanceService.create(new Provenance("modified", "CodeSystemVersion", version.getId().toString())
        .addContext("part-of", "CodeSystem", version.getCodeSystem()));
    return HttpResponse.created(version);
  }

  @Authorized(Privilege.CS_PUBLISH)
  @Post(uri = "/{codeSystem}/versions/{version}/activate")
  public HttpResponse<?> activateCodeSystemVersion(@PathVariable @ResourceId String codeSystem, @PathVariable String version) {
    codeSystemVersionService.activate(codeSystem, version);
    provenanceService.create(new Provenance("modified", "CodeSystemVersion", codeSystemVersionService.load(codeSystem, version).orElseThrow().getId().toString())
        .addContext("part-of", "CodeSystem", codeSystem));
    return HttpResponse.noContent();
  }

  @Authorized(Privilege.CS_PUBLISH)
  @Post(uri = "/{codeSystem}/versions/{version}/retire")
  public HttpResponse<?> retireCodeSystemVersion(@PathVariable @ResourceId String codeSystem, @PathVariable String version) {
    codeSystemVersionService.retire(codeSystem, version);
    provenanceService.create(new Provenance("modified", "CodeSystemVersion", codeSystemVersionService.load(codeSystem, version).orElseThrow().getId().toString())
        .addContext("part-of", "CodeSystem", codeSystem));
    return HttpResponse.noContent();
  }

  @Authorized(Privilege.CS_PUBLISH)
  @Post(uri = "/{codeSystem}/versions/{version}/draft")
  public HttpResponse<?> saveAsDraftCodeSystemVersion(@PathVariable @ResourceId String codeSystem, @PathVariable String version) {
    codeSystemVersionService.saveAsDraft(codeSystem, version);
    provenanceService.create(new Provenance("modified", "CodeSystemVersion", codeSystemVersionService.load(codeSystem, version).orElseThrow().getId().toString())
        .addContext("part-of", "CodeSystem", codeSystem));
    return HttpResponse.noContent();
  }

  @Authorized(Privilege.CS_EDIT)
  @Post(uri = "/{codeSystem}/versions/{version}/duplicate")
  public HttpResponse<?> duplicateCodeSystemVersion(@PathVariable String codeSystem, @PathVariable String version, @Body @Valid CodeSystemVersionDuplicateRequest request) {
    CodeSystemVersion newVersion =
        codeSystemDuplicateService.duplicateCodeSystemVersion(request.getVersion(), request.getCodeSystem(), version, codeSystem);
    provenanceService.create(new Provenance("created", "CodeSystemVersion", newVersion.getId().toString())
        .addContext("part-of", "CodeSystem", codeSystem));
    return HttpResponse.ok();
  }

  //----------------CodeSystem Concept----------------

  @Authorized(Privilege.CS_VIEW)
  @Get(uri = "/{codeSystem}/concepts{?params*}")
  public QueryResult<Concept> queryConcepts(@PathVariable @ResourceId String codeSystem, ConceptQueryParams params) {
    params.setPermittedCodeSystems(userPermissionService.getPermittedResourceIds("CodeSystem", "view"));
    params.setCodeSystem(codeSystem);
    return conceptService.query(params);
  }

  @Authorized(Privilege.CS_VIEW)
  @Get(uri = "/{codeSystem}/concepts/{code}")
  public Concept getConcept(@PathVariable @ResourceId String codeSystem, @PathVariable String code) {
    return conceptService.load(codeSystem, code).orElseThrow(() -> new NotFoundException("Concept not found: " + code));
  }

  @Authorized(Privilege.CS_VIEW)
  @Get(uri = "/{codeSystem}/versions/{version}/concepts/{code}")
  public Concept getConcept(@PathVariable @ResourceId String codeSystem, @PathVariable String version, @PathVariable String code) {
    return conceptService.load(codeSystem, version, code).orElseThrow(() -> new NotFoundException("Concept not found: " + code));
  }

  @Authorized(Privilege.CS_EDIT)
  @Post(uri = "/{codeSystem}/concepts")
  public HttpResponse<?> createConcept(@PathVariable @ResourceId String codeSystem, @QueryValue Optional<Boolean> full, @Body @Valid Concept concept) {
    concept.setId(null);
    concept.setCodeSystem(codeSystem);
    if (full.orElse(false)) {
      conceptService.saveWithVersions(concept, codeSystem);
    } else {
      conceptService.save(concept, codeSystem);
    }
    provenanceService.create(new Provenance("created", "CodeSystemEntity", concept.getId().toString())
        .addContext("part-of", "CodeSystem", codeSystem));
    return HttpResponse.created(concept);
  }

  @Authorized(Privilege.CS_EDIT)
  @Put(uri = "/{codeSystem}/concepts/{id}")
  public HttpResponse<?> updateConcept(@PathVariable @ResourceId String codeSystem, @PathVariable Long id, @QueryValue Optional<Boolean> full, @Body @Valid Concept concept) {
    concept.setId(id);
    concept.setCodeSystem(codeSystem);
    if (full.orElse(false)) {
      conceptService.saveWithVersions(concept, codeSystem);
    } else {
      conceptService.save(concept, codeSystem);
    }
    provenanceService.create(new Provenance("modified", "CodeSystemEntity", concept.getId().toString())
        .addContext("part-of", "CodeSystem", codeSystem));
    return HttpResponse.created(concept);
  }

  //----------------CodeSystem EntityVersion----------------

  @Authorized(Privilege.CS_VIEW)
  @Get(uri = "/{codeSystem}/entity-versions{?params*}")
  public QueryResult<CodeSystemEntityVersion> queryEntityVersions(@PathVariable @ResourceId String codeSystem, CodeSystemEntityVersionQueryParams params) {
    params.setPermittedCodeSystems(userPermissionService.getPermittedResourceIds("CodeSystem", "view"));
    params.setCodeSystem(codeSystem);
    return codeSystemEntityVersionService.query(params);
  }

  @Authorized(Privilege.CS_EDIT)
  @Post(uri = "/{codeSystem}/entities/{entityId}/versions")
  public HttpResponse<?> createEntityVersion(@PathVariable @ResourceId String codeSystem, @PathVariable Long entityId, @Body @Valid CodeSystemEntityVersion version) {
    version.setId(null);
    version.setCodeSystem(codeSystem);
    codeSystemEntityVersionService.save(version, entityId);
    provenanceService.create(new Provenance("created", "CodeSystemEntityVersion", version.getId().toString())
        .addContext("part-of", "CodeSystem", codeSystem)
        .addContext("part-of", "CodeSystemEntity", version.getCodeSystemEntityId().toString()));
    return HttpResponse.created(version);
  }

  @Authorized(Privilege.CS_EDIT)
  @Put(uri = "/{codeSystem}/entities/{entityId}/versions/{id}")
  public HttpResponse<?> updateEntityVersion(@PathVariable @ResourceId String codeSystem, @PathVariable Long entityId, @PathVariable Long id, @Body @Valid CodeSystemEntityVersion version) {
    version.setId(id);
    version.setCodeSystem(codeSystem);
    codeSystemEntityVersionService.save(version, entityId);
    provenanceService.create(new Provenance("modified", "CodeSystemEntityVersion", version.getId().toString())
        .addContext("part-of", "CodeSystem", codeSystem)
        .addContext("part-of", "CodeSystemEntity", version.getCodeSystemEntityId().toString()));
    return HttpResponse.created(version);
  }

  @Authorized(Privilege.CS_PUBLISH)
  @Post(uri = "/{codeSystem}/entities/versions/{id}/activate")
  public HttpResponse<?> activateEntityVersion(@PathVariable @ResourceId String codeSystem, @PathVariable Long id) {
    codeSystemEntityVersionService.activate(id);
    provenanceService.create(new Provenance("modified", "CodeSystemEntityVersion", id.toString())
        .addContext("part-of", "CodeSystem", codeSystem)
        .addContext("part-of", "CodeSystemEntity", codeSystemEntityVersionService.load(id).getCodeSystemEntityId().toString()));
    return HttpResponse.noContent();
  }

  @Authorized(Privilege.CS_PUBLISH)
  @Post(uri = "/{codeSystem}/entities/versions/{id}/retire")
  public HttpResponse<?> retireEntityVersion(@PathVariable @ResourceId String codeSystem, @PathVariable Long id) {
    codeSystemEntityVersionService.retire(id);
    provenanceService.create(new Provenance("modified", "CodeSystemEntityVersion", id.toString())
        .addContext("part-of", "CodeSystem", codeSystem)
        .addContext("part-of", "CodeSystemEntity", codeSystemEntityVersionService.load(id).getCodeSystemEntityId().toString()));
    return HttpResponse.noContent();
  }

  @Authorized(Privilege.CS_PUBLISH)
  @Post(uri = "/{codeSystem}/entities/versions/{id}/draft")
  public HttpResponse<?> saveAsDraftEntityVersion(@PathVariable @ResourceId String codeSystem, @PathVariable Long id) {
    codeSystemEntityVersionService.saveAsDraft(id);
    provenanceService.create(new Provenance("modified", "CodeSystemEntityVersion", id.toString())
        .addContext("part-of", "CodeSystem", codeSystem)
        .addContext("part-of", "CodeSystemEntity", codeSystemEntityVersionService.load(id).getCodeSystemEntityId().toString()));
    return HttpResponse.noContent();
  }

  @Authorized(Privilege.CS_EDIT)
  @Post(uri = "/{codeSystem}/entities/{entityId}/versions/{id}/duplicate")
  public HttpResponse<?> duplicateEntityVersion(@PathVariable String codeSystem,  @PathVariable Long entityId, @PathVariable Long id) {
    CodeSystemEntityVersion newVersion = codeSystemEntityVersionService.duplicate(codeSystem, entityId, id);
    provenanceService.create(new Provenance("created", "CodeSystemEntityVersion", newVersion.getId().toString())
        .addContext("part-of", "CodeSystem", codeSystem)
        .addContext("part-of", "CodeSystemEntity", newVersion.getCodeSystemEntityId().toString()));
    return HttpResponse.ok();
  }

  @Authorized(Privilege.CS_EDIT)
  @Post(uri = "/{codeSystem}/versions/{version}/entity-versions/{entityVersionId}/membership")
  public HttpResponse<?> linkEntityVersion(@PathVariable @ResourceId String codeSystem, @PathVariable String version, @PathVariable Long entityVersionId) {
    codeSystemVersionService.linkEntityVersion(codeSystem, version, entityVersionId);
    provenanceService.create(new Provenance("modified", "CodeSystemVersion", codeSystemVersionService.load(codeSystem, version).orElseThrow().getId().toString())
        .addContext("part-of", "CodeSystem", codeSystem));
    return HttpResponse.ok();
  }

  @Authorized(Privilege.CS_EDIT)
  @Delete(uri = "/{codeSystem}/versions/{version}/entity-versions/{entityVersionId}/membership")
  public HttpResponse<?> unlinkEntityVersion(@PathVariable @ResourceId String codeSystem, @PathVariable String version, @PathVariable Long entityVersionId) {
    codeSystemVersionService.unlinkEntityVersion(codeSystem, version, entityVersionId);
    provenanceService.create(new Provenance("modified", "CodeSystemVersion", codeSystemVersionService.load(codeSystem, version).orElseThrow().getId().toString())
        .addContext("part-of", "CodeSystem", codeSystem));
    return HttpResponse.ok();
  }

  //----------------CodeSystem Property----------------

  @Authorized(Privilege.CS_VIEW)
  @Get(uri = "/{codeSystem}/entity-properties{?params*}")
  public QueryResult<EntityProperty> queryEntityProperties(@PathVariable @ResourceId String codeSystem, EntityPropertyQueryParams params) {
    params.setPermittedCodeSystems(userPermissionService.getPermittedResourceIds("CodeSystem", "view"));
    params.setCodeSystem(codeSystem);
    return entityPropertyService.query(params);
  }

  @Authorized(Privilege.CS_VIEW)
  @Get(uri = "/{codeSystem}/entity-properties/{id}")
  public EntityProperty getEntityProperty(@PathVariable @ResourceId String codeSystem, @PathVariable Long id) {
    return entityPropertyService.load(id).orElseThrow(() -> new NotFoundException("EntityProperty not found: " + id));
  }

  @Authorized(Privilege.CS_EDIT)
  @Post(uri = "/{codeSystem}/entity-properties")
  public HttpResponse<?> createEntityProperty(@PathVariable @ResourceId String codeSystem, @Body @Valid EntityProperty property) {
    property.setId(null);
    entityPropertyService.save(property, codeSystem);
    provenanceService.create(new Provenance("modified", "CodeSystem", codeSystem));
    return HttpResponse.created(property);
  }

  @Authorized(Privilege.CS_EDIT)
  @Put(uri = "/{codeSystem}/entity-properties/{id}")
  public HttpResponse<?> updateEntityProperty(@PathVariable @ResourceId String codeSystem, @PathVariable Long id, @Body @Valid EntityProperty property) {
    property.setId(id);
    entityPropertyService.save(property, codeSystem);
    provenanceService.create(new Provenance("modified", "CodeSystem", codeSystem));
    return HttpResponse.created(property);
  }

  @Authorized(Privilege.CS_EDIT)
  @Delete(uri = "/{codeSystem}/entity-properties/{id}")
  public HttpResponse<?> deleteEntityProperty(@PathVariable @ResourceId String codeSystem, @PathVariable Long id) {
    entityPropertyService.cancel(id, codeSystem);
    provenanceService.create(new Provenance("modified", "CodeSystem", codeSystem));
    return HttpResponse.ok();
  }

  @Authorized(Privilege.CS_EDIT)
  @Delete(uri = "/{codeSystem}/entity-property-usages/{propertyId}")
  public HttpResponse<?> deleteEntityPropertyUsages(@PathVariable @ResourceId String codeSystem, @PathVariable Long propertyId) {
    entityPropertyService.deleteUsages(propertyId, codeSystem);
    provenanceService.create(new Provenance("modified", "CodeSystem", codeSystem));
    return HttpResponse.ok();
  }

  //----------------CodeSystem PropertyValue----------------

  @Authorized(Privilege.CS_VIEW)
  @Get(uri = "/{codeSystem}/entity-property-values/{id}")
  public EntityPropertyValue getEntityPropertyValue(@PathVariable @ResourceId String codeSystem, @PathVariable Long id) {
    return entityPropertyValueService.load(id).orElseThrow(() -> new NotFoundException("EntityPropertyValue not found: " + id));
  }

  @Authorized(Privilege.CS_EDIT)
  @Post(uri = "/{codeSystem}/entity-versions/{entityVersionId}/entity-property-values")
  public HttpResponse<?> createEntityPropertyValue(@PathVariable @ResourceId String codeSystem, @PathVariable Long entityVersionId, @Body @Valid EntityPropertyValue propertyValue) {
    propertyValue.setId(null);
    entityPropertyValueService.save(propertyValue, entityVersionId, codeSystem);
    provenanceService.create(new Provenance("modified", "CodeSystemEntityVersion", entityVersionId.toString())
        .addContext("part-of", "CodeSystem", codeSystem)
        .addContext("part-of", "CodeSystemEntity", codeSystemEntityVersionService.load(entityVersionId).getCodeSystemEntityId().toString()));
    return HttpResponse.created(propertyValue);
  }

  @Authorized(Privilege.CS_EDIT)
  @Put(uri = "/{codeSystem}/entity-versions/{entityVersionId}/entity-property-values/{id}")
  public HttpResponse<?> updateEntityPropertyValue(@PathVariable @ResourceId String codeSystem, @PathVariable Long entityVersionId, @PathVariable Long id, @Body @Valid EntityPropertyValue propertyValue) {
    propertyValue.setId(id);
    entityPropertyValueService.save(propertyValue, entityVersionId, codeSystem);
    provenanceService.create(new Provenance("modified", "CodeSystemEntityVersion", entityVersionId.toString())
        .addContext("part-of", "CodeSystem", codeSystem)
        .addContext("part-of", "CodeSystemEntity", codeSystemEntityVersionService.load(entityVersionId).getCodeSystemEntityId().toString()));
    return HttpResponse.created(propertyValue);
  }

  //----------------CodeSystem Designation---------------

  @Authorized(Privilege.CS_VIEW)
  @Get(uri = "/{codeSystem}/designations/{id}")
  public Designation getDesignation(@PathVariable @ResourceId String codeSystem, @PathVariable Long id) {
    return designationService.load(id).orElseThrow(() -> new NotFoundException("Designation not found: " + id));
  }

  @Authorized(Privilege.CS_EDIT)
  @Post(uri = "/{codeSystem}/entity-versions/{entityVersionId}/designations")
  public HttpResponse<?> createDesignation(@PathVariable @ResourceId String codeSystem, @PathVariable Long entityVersionId, @Body @Valid Designation designation) {
    designation.setId(null);
    designationService.save(designation, entityVersionId, codeSystem);
    provenanceService.create(new Provenance("modified", "CodeSystemEntityVersion", entityVersionId.toString())
        .addContext("part-of", "CodeSystem", codeSystem)
        .addContext("part-of", "CodeSystemEntity", codeSystemEntityVersionService.load(entityVersionId).getCodeSystemEntityId().toString()));
    return HttpResponse.created(designation);
  }

  @Authorized(Privilege.CS_EDIT)
  @Put(uri = "/{codeSystem}/entity-versions/{entityVersionId}/designations/{id}")
  public HttpResponse<?> updateDesignation(@PathVariable @ResourceId String codeSystem, @PathVariable Long entityVersionId, @PathVariable Long id, @Body @Valid Designation designation) {
    designation.setId(id);
    designationService.save(designation, entityVersionId, codeSystem);
    provenanceService.create(new Provenance("modified", "CodeSystemEntityVersion", entityVersionId.toString())
        .addContext("part-of", "CodeSystem", codeSystem)
        .addContext("part-of", "CodeSystemEntity", codeSystemEntityVersionService.load(entityVersionId).getCodeSystemEntityId().toString()));
    return HttpResponse.created(designation);
  }

  //----------------CodeSystem Association---------------

  @Authorized(Privilege.CS_VIEW)
  @Get(uri = "/{codeSystem}/associations/{id}")
  public CodeSystemAssociation getAssociation(@PathVariable String codeSystem, @PathVariable Long id) {
    return associationService.load(id).orElseThrow(() -> new NotFoundException("Association not found: " + id));
  }

  @Authorized(Privilege.CS_EDIT)
  @Post(uri = "/{codeSystem}/entity-versions/{entityVersionId}/associations")
  public HttpResponse<?> createAssociation(@PathVariable @ResourceId String codeSystem, @PathVariable Long entityVersionId, @Body @Valid CodeSystemAssociation association) {
    association.setId(null);
    associationService.save(association, entityVersionId, codeSystem);
    provenanceService.create(new Provenance("modified", "CodeSystemEntityVersion", entityVersionId.toString())
        .addContext("part-of", "CodeSystem", codeSystem)
        .addContext("part-of", "CodeSystemEntity", codeSystemEntityVersionService.load(entityVersionId).getCodeSystemEntityId().toString()));
    return HttpResponse.created(association);
  }

  @Authorized(Privilege.CS_EDIT)
  @Put(uri = "/{codeSystem}/entity-versions/{entityVersionId}/associations/{id}")
  public HttpResponse<?> updateAssociation(@PathVariable @ResourceId String codeSystem, @PathVariable Long entityVersionId, @PathVariable Long id, @Body @Valid CodeSystemAssociation association) {
    association.setId(id);
    associationService.save(association, entityVersionId, codeSystem);
    provenanceService.create(new Provenance("modified", "CodeSystemEntityVersion", entityVersionId.toString())
        .addContext("part-of", "CodeSystem", codeSystem)
        .addContext("part-of", "CodeSystemEntity", codeSystemEntityVersionService.load(entityVersionId).getCodeSystemEntityId().toString()));
    return HttpResponse.created(association);
  }

  @Authorized(Privilege.CS_EDIT)
  @Delete(uri = "/{codeSystem}/associations/{id}")
  public HttpResponse<?> deleteAssociation(@PathVariable @ResourceId String codeSystem, @PathVariable Long id) {
    associationService.delete(id, codeSystem);
    return HttpResponse.ok();
  }

  //----------------CodeSystem Supplement----------------

  @Authorized(Privilege.CS_VIEW)
  @Get(uri = "/{codeSystem}/supplements")
  public List<CodeSystemSupplement> getSupplements(@PathVariable @ResourceId String codeSystem) {
    return codeSystemSupplementService.getSupplements(codeSystem);
  }

  @Authorized(Privilege.CS_VIEW)
  @Get(uri = "/{codeSystem}/supplements/{id}")
  public CodeSystemSupplement getSupplement(@PathVariable @ResourceId String codeSystem, @PathVariable Long id) {
    return codeSystemSupplementService.load(id).orElseThrow(() -> new NotFoundException("Supplement not found: " + id));
  }

  @Authorized(Privilege.CS_EDIT)
  @Post(uri = "/{codeSystem}/supplements")
  public HttpResponse<?> createSupplement(@PathVariable @ResourceId String codeSystem, @Body @Valid CodeSystemSupplement supplement) {
    codeSystemSupplementService.save(supplement, codeSystem);
    provenanceService.create(new Provenance("modified", "CodeSystem", codeSystem));
    return HttpResponse.created(supplement);
  }

  @Authorized(Privilege.CS_EDIT)
  @Put(uri = "/{codeSystem}/supplements/{id}")
  public HttpResponse<?> updateSupplement(@PathVariable @ResourceId String codeSystem, @PathVariable Long id, @Body @Valid CodeSystemSupplement supplement) {
    supplement.setId(id);
    codeSystemSupplementService.save(supplement, codeSystem);
    provenanceService.create(new Provenance("modified", "CodeSystem", codeSystem));
    return HttpResponse.created(supplement);
  }

  @Authorized(Privilege.CS_EDIT)
  @Post(uri = "/{codeSystem}/entities/versions/{entityVersionId}/supplements")
  public HttpResponse<?> createSupplement(@PathVariable @ResourceId String codeSystem, @PathVariable Long entityVersionId, @Body @Valid CodeSystemSupplement supplement) {
    codeSystemSupplementService.save(supplement, entityVersionId, codeSystem);
    provenanceService.create(new Provenance("modified", "CodeSystemEntityVersion", entityVersionId.toString())
        .addContext("part-of", "CodeSystem", codeSystem)
        .addContext("part-of", "CodeSystemEntity",  codeSystemEntityVersionService.load(entityVersionId).getCodeSystemEntityId().toString()));
    return HttpResponse.created(supplement);
  }

  @Authorized(Privilege.CS_EDIT)
  @Put(uri = "/{codeSystem}/entities/versions/{entityVersionId}/supplements/{id}")
  public HttpResponse<?> createSupplement(@PathVariable String codeSystem, @PathVariable Long entityVersionId, @PathVariable Long id, @Body @Valid CodeSystemSupplement supplement) {
    supplement.setId(id);
    codeSystemSupplementService.save(supplement, entityVersionId, codeSystem);
    provenanceService.create(new Provenance("modified", "CodeSystemEntityVersion", entityVersionId.toString())
        .addContext("part-of", "CodeSystem", codeSystem)
        .addContext("part-of", "CodeSystemEntity",  codeSystemEntityVersionService.load(entityVersionId).getCodeSystemEntityId().toString()));
    return HttpResponse.created(supplement);
  }

  @Getter
  @Setter
  @Introspected
  public static class CodeSystemDuplicateRequest {
    private String codeSystem;
    private String codeSystemUri;
  }

  @Getter
  @Setter
  @Introspected
  public static class CodeSystemVersionDuplicateRequest {
    private String codeSystem;
    private String version;
  }
}
