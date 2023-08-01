package com.kodality.termx.terminology.codesystem;

import com.kodality.commons.exception.NotFoundException;
import com.kodality.commons.model.QueryResult;
import com.kodality.termx.Privilege;
import com.kodality.termx.auth.Authorized;
import com.kodality.termx.auth.ResourceId;
import com.kodality.termx.auth.UserPermissionService;
import com.kodality.termx.sys.provenance.Provenance;
import com.kodality.termx.sys.provenance.ProvenanceService;
import com.kodality.termx.terminology.codesystem.concept.ConceptService;
import com.kodality.termx.terminology.codesystem.entity.CodeSystemEntityVersionService;
import com.kodality.termx.terminology.codesystem.entityproperty.EntityPropertyService;
import com.kodality.termx.ts.codesystem.CodeSystem;
import com.kodality.termx.ts.codesystem.CodeSystemEntityVersion;
import com.kodality.termx.ts.codesystem.CodeSystemEntityVersionQueryParams;
import com.kodality.termx.ts.codesystem.CodeSystemQueryParams;
import com.kodality.termx.ts.codesystem.CodeSystemTransactionRequest;
import com.kodality.termx.ts.codesystem.CodeSystemVersion;
import com.kodality.termx.ts.codesystem.CodeSystemVersionQueryParams;
import com.kodality.termx.ts.codesystem.CodeSystemVersionReference;
import com.kodality.termx.ts.codesystem.Concept;
import com.kodality.termx.ts.codesystem.ConceptQueryParams;
import com.kodality.termx.ts.codesystem.ConceptTransactionRequest;
import com.kodality.termx.ts.codesystem.EntityProperty;
import com.kodality.termx.ts.codesystem.EntityPropertyQueryParams;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import java.util.List;
import java.util.Map;
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
  private final CodeSystemVersionService codeSystemVersionService;
  private final CodeSystemDuplicateService codeSystemDuplicateService;
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
  @Post("/transaction")
  public HttpResponse<?> saveCodeSystemTransaction(@Body @Valid CodeSystemTransactionRequest request) {
    codeSystemService.save(request);
    provenanceService.create(new Provenance("modified", "CodeSystem", request.getCodeSystem().getId()));
    return HttpResponse.created(request.getCodeSystem());
  }

  @Authorized(Privilege.CS_EDIT)
  @Post(uri = "/{codeSystem}/duplicate")
  public HttpResponse<?> duplicateCodeSystem(@PathVariable String codeSystem, @Body @Valid CodeSystemDuplicateRequest request) {
    CodeSystem targetCodeSystem = new CodeSystem().setId(request.getCodeSystem()).setUri(request.getCodeSystemUri());
    codeSystemDuplicateService.duplicateCodeSystem(targetCodeSystem, codeSystem);
    provenanceService.create(new Provenance("created", "CodeSystem", targetCodeSystem.getId()));
    return HttpResponse.ok();
  }

  @Authorized(Privilege.CS_EDIT)
  @Post(uri = "/{codeSystem}/change-id")
  public HttpResponse<?> changeCodeSystemId(@PathVariable String codeSystem, @Valid @Body Map<String, String> body) {
    String newId = body.get("id");
    codeSystemService.changeId(codeSystem, newId);
    provenanceService.create(new Provenance("modified", "CodeSystem", newId));
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
  public HttpResponse<?> createCodeSystemVersion(@PathVariable @ResourceId String codeSystem, @Body @Valid CodeSystemVersion codeSystemVersion) {
    codeSystemVersion.setId(null);
    codeSystemVersion.setCodeSystem(codeSystem);
    codeSystemVersionService.save(codeSystemVersion);
    provenanceService.create(new Provenance("created", "CodeSystemVersion", codeSystemVersion.getId().toString())
        .addContext("part-of", "CodeSystem", codeSystemVersion.getCodeSystem()));
    return HttpResponse.created(codeSystemVersion);
  }

  @Authorized(Privilege.CS_EDIT)
  @Put(uri = "/{codeSystem}/versions/{version}")
  public HttpResponse<?> updateCodeSystemVersion(@PathVariable @ResourceId String codeSystem, @PathVariable String version, @Body @Valid CodeSystemVersion codeSystemVersion) {
    codeSystemVersion.setVersion(version);
    codeSystemVersion.setCodeSystem(codeSystem);
    codeSystemVersionService.save(codeSystemVersion);
    provenanceService.create(new Provenance("modified", "CodeSystemVersion", codeSystemVersion.getId().toString())
        .addContext("part-of", "CodeSystem", codeSystemVersion.getCodeSystem()));
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
  public HttpResponse<?> duplicateCodeSystemVersion(@PathVariable @ResourceId String codeSystem, @PathVariable String version, @Body @Valid CodeSystemVersionDuplicateRequest request) {
    CodeSystemVersion newVersion = codeSystemDuplicateService.duplicateCodeSystemVersion(request.getVersion(), request.getCodeSystem(), version, codeSystem);
    provenanceService.create(new Provenance("created", "CodeSystemVersion", newVersion.getId().toString())
        .addContext("part-of", "CodeSystem", codeSystem));
    return HttpResponse.ok();
  }

  @Authorized(Privilege.CS_PUBLISH)
  @Delete(uri = "/{codeSystem}/versions/{version}")
  public HttpResponse<?> deleteCodeSystemVersion(@PathVariable @ResourceId String codeSystem, @PathVariable String version) {
    Long versionId = codeSystemVersionService.load(codeSystem, version).map(CodeSystemVersionReference::getId).orElseThrow();
    codeSystemVersionService.cancel(versionId, codeSystem);
    provenanceService.create(new Provenance("deleted", "CodeSystemVersion", versionId.toString()));
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
  @Post(uri = "/{codeSystem}/concepts/{code}/propagate-properties")
  public HttpResponse<?> propagateProperties(@PathVariable @ResourceId String codeSystem, @PathVariable String code, @Body @Valid CodeSystemConceptPropertyPropagationRequest request) {
    conceptService.propagateProperties(code, request.getTargetConceptIds(), codeSystem);
    request.getTargetConceptIds().forEach(id -> provenanceService.create(new Provenance("created", "CodeSystemEntity", id.toString())
        .addContext("part-of", "CodeSystem", codeSystem)));
    return HttpResponse.ok();
  }

  @Authorized(Privilege.CS_EDIT)
  @Post(uri = "/{codeSystem}/concepts/transaction")
  public HttpResponse<?> saveConceptTransaction(@PathVariable @ResourceId String codeSystem, @Body @Valid ConceptTransactionRequest request) {
    request.setCodeSystem(codeSystem);
    request.setCodeSystemVersion(null);
    Concept concept = conceptService.save(request);
    provenanceService.create(new Provenance("created", "CodeSystemEntity", concept.getId().toString())
        .addContext("part-of", "CodeSystem", codeSystem));
    return HttpResponse.created(concept);
  }

  @Authorized(Privilege.CS_VIEW)
  @Get(uri = "/{codeSystem}/versions/{version}/concepts/{code}")
  public Concept getConcept(@PathVariable @ResourceId String codeSystem, @PathVariable String version, @PathVariable String code) {
    return conceptService.load(codeSystem, version, code).orElseThrow(() -> new NotFoundException("Concept not found: " + code));
  }

  @Authorized(Privilege.CS_EDIT)
  @Post(uri = "/{codeSystem}/versions/{version}/concepts/transaction")
  public HttpResponse<?> saveConceptTransaction(@PathVariable @ResourceId String codeSystem, @PathVariable String version, @Body @Valid ConceptTransactionRequest request) {
    request.setCodeSystem(codeSystem);
    request.setCodeSystemVersion(version);
    Concept concept = conceptService.save(request);
    provenanceService.create(new Provenance("created", "CodeSystemEntity", concept.getId().toString())
        .addContext("part-of", "CodeSystem", codeSystem));
    return HttpResponse.created(concept);
  }

  @Authorized(Privilege.CS_EDIT)
  @Post(uri = "/{codeSystem}/versions/{version}/concepts/link")
  public HttpResponse<?> linkEntityVersion(@PathVariable @ResourceId String codeSystem, @PathVariable String version,  @Body CodeSystemEntityVersionLinkRequest request) {
    codeSystemVersionService.linkEntityVersions(codeSystem, version, request.getEntityVersionIds());
    provenanceService.create(new Provenance("modified", "CodeSystemVersion", codeSystemVersionService.load(codeSystem, version).orElseThrow().getId().toString())
        .addContext("part-of", "CodeSystem", codeSystem));
    return HttpResponse.ok();
  }

  @Authorized(Privilege.CS_EDIT)
  @Post(uri = "/{codeSystem}/versions/{version}/concepts/unlink")
  public HttpResponse<?> unlinkEntityVersion(@PathVariable @ResourceId String codeSystem, @PathVariable String version, @Body CodeSystemEntityVersionLinkRequest request) {
    codeSystemVersionService.unlinkEntityVersions(codeSystem, version, request.getEntityVersionIds());
    provenanceService.create(new Provenance("modified", "CodeSystemVersion", codeSystemVersionService.load(codeSystem, version).orElseThrow().getId().toString())
        .addContext("part-of", "CodeSystem", codeSystem));
    return HttpResponse.ok();
  }

  //----------------CodeSystem EntityVersion----------------

  @Authorized(Privilege.CS_VIEW)
  @Get(uri = "/{codeSystem}/entity-versions{?params*}")
  public QueryResult<CodeSystemEntityVersion> queryEntityVersions(@PathVariable @ResourceId String codeSystem, CodeSystemEntityVersionQueryParams params) {
    params.setPermittedCodeSystems(userPermissionService.getPermittedResourceIds("CodeSystem", "view"));
    params.setCodeSystem(codeSystem);
    return codeSystemEntityVersionService.query(params);
  }

  @Authorized(Privilege.CS_PUBLISH)
  @Post(uri = "/{codeSystem}/entity-versions/{id}/activate")
  public HttpResponse<?> activateEntityVersion(@PathVariable @ResourceId String codeSystem, @PathVariable Long id) {
    codeSystemEntityVersionService.activate(id);
    provenanceService.create(new Provenance("modified", "CodeSystemEntityVersion", id.toString())
        .addContext("part-of", "CodeSystem", codeSystem)
        .addContext("part-of", "CodeSystemEntity", codeSystemEntityVersionService.load(id).getCodeSystemEntityId().toString()));
    return HttpResponse.noContent();
  }

  @Authorized(Privilege.CS_PUBLISH)
  @Post(uri = "/{codeSystem}/entity-versions/{id}/retire")
  public HttpResponse<?> retireEntityVersion(@PathVariable @ResourceId String codeSystem, @PathVariable Long id) {
    codeSystemEntityVersionService.retire(id);
    provenanceService.create(new Provenance("modified", "CodeSystemEntityVersion", id.toString())
        .addContext("part-of", "CodeSystem", codeSystem)
        .addContext("part-of", "CodeSystemEntity", codeSystemEntityVersionService.load(id).getCodeSystemEntityId().toString()));
    return HttpResponse.noContent();
  }

  @Authorized(Privilege.CS_PUBLISH)
  @Post(uri = "/{codeSystem}/entity-versions/{id}/draft")
  public HttpResponse<?> saveAsDraftEntityVersion(@PathVariable @ResourceId String codeSystem, @PathVariable Long id) {
    codeSystemEntityVersionService.saveAsDraft(id);
    provenanceService.create(new Provenance("modified", "CodeSystemEntityVersion", id.toString())
        .addContext("part-of", "CodeSystem", codeSystem)
        .addContext("part-of", "CodeSystemEntity", codeSystemEntityVersionService.load(id).getCodeSystemEntityId().toString()));
    return HttpResponse.noContent();
  }

  @Authorized(Privilege.CS_EDIT)
  @Delete(uri = "/{codeSystem}/entity-versions/{id}")
  public HttpResponse<?> deleteEntityVersion(@PathVariable @ResourceId String codeSystem, @PathVariable Long id) {
    codeSystemEntityVersionService.cancel(id, codeSystem);
    return HttpResponse.noContent();
  }

  @Authorized(Privilege.CS_EDIT)
  @Post(uri = "/{codeSystem}/entity-versions/{id}/duplicate")
  public HttpResponse<?> duplicateEntityVersion(@PathVariable String codeSystem, @PathVariable Long id) {
    CodeSystemEntityVersion newVersion = codeSystemEntityVersionService.duplicate(codeSystem, id);
    provenanceService.create(new Provenance("created", "CodeSystemEntityVersion", newVersion.getId().toString())
        .addContext("part-of", "CodeSystem", codeSystem)
        .addContext("part-of", "CodeSystemEntity", newVersion.getCodeSystemEntityId().toString()));
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
  @Post(uri = "/{codeSystem}/entity-properties/{propertyId}/delete-usages")
  public HttpResponse<?> deleteEntityPropertyUsages(@PathVariable @ResourceId String codeSystem, @PathVariable Long propertyId) {
    entityPropertyService.deleteUsages(propertyId, codeSystem);
    provenanceService.create(new Provenance("modified", "CodeSystem", codeSystem));
    return HttpResponse.ok();
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

  @Getter
  @Setter
  @Introspected
  public static class CodeSystemEntityVersionLinkRequest {
    private List<Long> entityVersionIds;
  }

  @Getter
  @Setter
  @Introspected
  public static class CodeSystemConceptPropertyPropagationRequest {
    private List<Long> targetConceptIds;
  }
}
