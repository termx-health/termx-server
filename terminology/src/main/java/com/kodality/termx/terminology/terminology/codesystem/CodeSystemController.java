package com.kodality.termx.terminology.terminology.codesystem;

import com.kodality.commons.exception.NotFoundException;
import com.kodality.commons.model.QueryResult;
import com.kodality.termx.core.sys.lorque.LorqueProcessService;
import com.kodality.termx.sys.lorque.LorqueProcess;
import com.kodality.termx.terminology.Privilege;
import com.kodality.termx.core.auth.Authorized;
import com.kodality.termx.core.auth.SessionStore;
import com.kodality.termx.core.sys.provenance.Provenance;
import com.kodality.termx.core.sys.provenance.Provenance.ProvenanceChange;
import com.kodality.termx.terminology.terminology.codesystem.association.CodeSystemAssociationService;
import com.kodality.termx.terminology.terminology.codesystem.concept.ConceptExportService;
import com.kodality.termx.terminology.terminology.codesystem.concept.ConceptService;
import com.kodality.termx.terminology.terminology.codesystem.entity.CodeSystemEntityVersionService;
import com.kodality.termx.terminology.terminology.codesystem.entity.CodeSystemSupplementService;
import com.kodality.termx.terminology.terminology.codesystem.entityproperty.EntityPropertyService;
import com.kodality.termx.terminology.terminology.codesystem.version.CodeSystemVersionService;
import com.kodality.termx.ts.codesystem.CodeSystem;
import com.kodality.termx.ts.codesystem.CodeSystemAssociation;
import com.kodality.termx.ts.codesystem.CodeSystemConceptSupplementRequest;
import com.kodality.termx.ts.codesystem.CodeSystemEntityVersion;
import com.kodality.termx.ts.codesystem.CodeSystemEntityVersionQueryParams;
import com.kodality.termx.ts.codesystem.CodeSystemQueryParams;
import com.kodality.termx.ts.codesystem.CodeSystemTransactionRequest;
import com.kodality.termx.ts.codesystem.CodeSystemVersion;
import com.kodality.termx.ts.codesystem.CodeSystemVersionQueryParams;
import com.kodality.termx.ts.codesystem.Concept;
import com.kodality.termx.ts.codesystem.ConceptQueryParams;
import com.kodality.termx.ts.codesystem.ConceptTransactionRequest;
import com.kodality.termx.ts.codesystem.EntityProperty;
import com.kodality.termx.ts.codesystem.EntityPropertyQueryParams;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.annotation.QueryValue;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
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
  private final ConceptExportService conceptExportService;
  private final CodeSystemService codeSystemService;
  private final EntityPropertyService entityPropertyService;
  private final CodeSystemVersionService codeSystemVersionService;
  private final CodeSystemDuplicateService codeSystemDuplicateService;
  private final CodeSystemAssociationService codeSystemAssociationService;
  private final CodeSystemEntityVersionService codeSystemEntityVersionService;
  private final CodeSystemSupplementService codeSystemSupplementService;
  private final CodeSystemProvenanceService provenanceService;
  private final LorqueProcessService lorqueProcessService;

  //----------------CodeSystem----------------

  @Authorized(Privilege.CS_VIEW)
  @Get(uri = "{?params*}")
  public QueryResult<CodeSystem> queryCodeSystems(CodeSystemQueryParams params) {
    params.setPermittedIds(SessionStore.require().getPermittedResourceIds(Privilege.CS_VIEW));
    return codeSystemService.query(params);
  }

  @Authorized(Privilege.CS_VIEW)
  @Get(uri = "/{codeSystem}{?decorate}")
  public CodeSystem getCodeSystem(@PathVariable String codeSystem, Optional<Boolean> decorate) {
    return codeSystemService.load(codeSystem, decorate.orElse(false)).orElseThrow(() -> new NotFoundException("CodeSystem not found: " + codeSystem));
  }


  @Authorized(Privilege.CS_EDIT)
  @Post("/transaction")
  public HttpResponse<?> saveCodeSystemTransaction(@Body @Valid CodeSystemTransactionRequest request) {
    SessionStore.require().checkPermitted(request.getCodeSystem().getId(), Privilege.CS_EDIT);
    provenanceService.provenanceCodeSystemTransactionRequest("save", request, () -> {
      codeSystemService.save(request);
    });
    return HttpResponse.created(request.getCodeSystem());
  }

  @Authorized(Privilege.CS_EDIT)
  @Post(uri = "/{codeSystem}/duplicate")
  public HttpResponse<?> duplicateCodeSystem(@PathVariable String codeSystem, @Body @Valid CodeSystemDuplicateRequest request) {
    SessionStore.require().checkPermitted(request.getCodeSystem(), Privilege.CS_EDIT);
    provenanceService.provenanceCodeSystem("duplicate", request.getCodeSystem(), () -> {
      CodeSystem targetCodeSystem = new CodeSystem().setId(request.getCodeSystem()).setUri(request.getCodeSystemUri());
      codeSystemDuplicateService.duplicateCodeSystem(targetCodeSystem, codeSystem);
    });
    return HttpResponse.ok();
  }

  @Authorized(Privilege.CS_EDIT)
  @Post(uri = "/{codeSystem}/change-id")
  public HttpResponse<?> changeCodeSystemId(@PathVariable String codeSystem, @Valid @Body Map<String, String> body) {
    String newId = body.get("id");
    codeSystemService.changeId(codeSystem, newId);
    provenanceService.create(new Provenance("change-id", "CodeSystem", newId).setChanges(Map.of("id", ProvenanceChange.of(codeSystem, newId))));
    return HttpResponse.ok();
  }

  @Authorized(Privilege.CS_PUBLISH)
  @Delete(uri = "/{codeSystem}")
  public HttpResponse<?> deleteCodeSystem(@PathVariable String codeSystem) {
    codeSystemService.cancel(codeSystem);
    provenanceService.create(new Provenance("delete", "CodeSystem", codeSystem));
    return HttpResponse.ok();
  }

  //----------------CodeSystem Version----------------

  @Authorized(Privilege.CS_VIEW)
  @Get(uri = "/{codeSystem}/versions{?params*}")
  public QueryResult<CodeSystemVersion> queryCodeSystemVersions(@PathVariable String codeSystem, CodeSystemVersionQueryParams params) {
    params.setPermittedCodeSystems(SessionStore.require().getPermittedResourceIds(Privilege.CS_VIEW));
    params.setCodeSystem(codeSystem);
    return codeSystemVersionService.query(params);
  }

  @Authorized(Privilege.CS_VIEW)
  @Get(uri = "/{codeSystem}/versions/{version}")
  public CodeSystemVersion getCodeSystemVersion(@PathVariable String codeSystem, @PathVariable String version) {
    return codeSystemVersionService.load(codeSystem, version).orElseThrow(() -> new NotFoundException("CodeSystemVersion not found: " + codeSystem));
  }

  @Authorized(Privilege.CS_EDIT)
  @Post(uri = "/{codeSystem}/versions")
  public HttpResponse<?> createCodeSystemVersion(@PathVariable String codeSystem, @Body @Valid CodeSystemVersion codeSystemVersion) {
    codeSystemVersion.setId(null);
    codeSystemVersion.setCodeSystem(codeSystem);
    provenanceService.provenanceCodeSystemVersion("save", codeSystem, codeSystemVersion.getVersion(), () -> {
      codeSystemVersionService.save(codeSystemVersion);
    });
    return HttpResponse.created(codeSystemVersion);
  }

  @Authorized(Privilege.CS_EDIT)
  @Put(uri = "/{codeSystem}/versions/{version}")
  public HttpResponse<?> updateCodeSystemVersion(@PathVariable String codeSystem, @PathVariable String version,
                                                 @Body @Valid CodeSystemVersion codeSystemVersion) {
    codeSystemVersion.setVersion(version);
    codeSystemVersion.setCodeSystem(codeSystem);
    provenanceService.provenanceCodeSystemVersion("save", codeSystem, version, () -> {
      codeSystemVersionService.save(codeSystemVersion);
    });
    return HttpResponse.created(version);
  }

  @Authorized(Privilege.CS_PUBLISH)
  @Post(uri = "/{codeSystem}/versions/{version}/activate")
  public HttpResponse<?> activateCodeSystemVersion(@PathVariable String codeSystem, @PathVariable String version) {
    provenanceService.provenanceCodeSystemVersion("activate", codeSystem, version, () -> {
      codeSystemVersionService.activate(codeSystem, version);
    });
    return HttpResponse.noContent();
  }

  @Authorized(Privilege.CS_PUBLISH)
  @Post(uri = "/{codeSystem}/versions/{version}/retire")
  public HttpResponse<?> retireCodeSystemVersion(@PathVariable String codeSystem, @PathVariable String version) {
    provenanceService.provenanceCodeSystemVersion("retire", codeSystem, version, () -> {
      codeSystemVersionService.retire(codeSystem, version);
    });
    return HttpResponse.noContent();
  }

  @Authorized(Privilege.CS_PUBLISH)
  @Post(uri = "/{codeSystem}/versions/{version}/draft")
  public HttpResponse<?> saveAsDraftCodeSystemVersion(@PathVariable String codeSystem, @PathVariable String version) {
    provenanceService.provenanceCodeSystemVersion("save", codeSystem, version, () -> {
      codeSystemVersionService.saveAsDraft(codeSystem, version);
    });
    return HttpResponse.noContent();
  }

  @Authorized(Privilege.CS_EDIT)
  @Post(uri = "/{codeSystem}/versions/{version}/duplicate")
  public HttpResponse<?> duplicateCodeSystemVersion(@PathVariable String codeSystem, @PathVariable String version,
                                                    @Body @Valid CodeSystemVersionDuplicateRequest request) {
    SessionStore.require().checkPermitted(request.getCodeSystem(), Privilege.CS_EDIT);
    provenanceService.provenanceCodeSystemVersion("duplicate", codeSystem, request.getVersion(), () -> {
      codeSystemDuplicateService.duplicateCodeSystemVersion(request.getVersion(), request.getCodeSystem(), version, codeSystem);
    });
    return HttpResponse.ok();
  }

  @Authorized(Privilege.CS_PUBLISH)
  @Delete(uri = "/{codeSystem}/versions/{version}")
  public HttpResponse<?> deleteCodeSystemVersion(@PathVariable String codeSystem, @PathVariable String version) {
    CodeSystemVersion csv = codeSystemVersionService.load(codeSystem, version).orElseThrow();
    codeSystemVersionService.cancel(csv.getId(), codeSystem);
    provenanceService.create(new Provenance("deleted", "CodeSystemVersion", csv.getId().toString(), csv.getVersion())
        .addContext("part-of", "CodeSystem", csv.getCodeSystem()));
    return HttpResponse.ok();
  }

  //----------------CodeSystem Concept----------------

  @Authorized(Privilege.CS_VIEW)
  @Get(uri = "/{codeSystem}/concepts{?params*}")
  public QueryResult<Concept> queryConcepts(@PathVariable String codeSystem, ConceptQueryParams params) {
    params.setCodeSystem(codeSystem);
    return conceptService.query(params);
  }

  @Authorized(Privilege.CS_VIEW)
  @Get(uri = "/{codeSystem}/concepts/{code}")
  public Concept getConcept(@PathVariable String codeSystem, @PathVariable String code) {
    return conceptService.load(codeSystem, parseCode(code)).orElseThrow(() -> new NotFoundException("Concept not found: " + parseCode(code)));
  }

  @Authorized(Privilege.CS_EDIT)
  @Post(uri = "/{codeSystem}/concepts/{code}/propagate-properties")
  public HttpResponse<?> propagateProperties(@PathVariable String codeSystem, @PathVariable String code,
                                             @Body @Valid CodeSystemConceptPropertyPropagationRequest request) {
    conceptService.propagateProperties(parseCode(code), request.getTargetConceptIds(), codeSystem);
    return HttpResponse.ok();
  }

  @Authorized(Privilege.CS_EDIT)
  @Post(uri = "/{codeSystem}/concepts/transaction")
  public HttpResponse<?> saveConceptTransaction(@PathVariable String codeSystem, @Body @Valid ConceptTransactionRequest request) {
    request.setCodeSystem(codeSystem);
    request.setCodeSystemVersion(null);
    Concept concept = provenanceService.provenanceConceptTransaction("save", request, () -> conceptService.save(request));
    return HttpResponse.created(concept);
  }

  @Authorized(Privilege.CS_EDIT)
  @Delete(uri = "/{codeSystem}/concepts/{code}")
  public HttpResponse<?> deleteConcept(@PathVariable String codeSystem, @PathVariable String code) {
    Concept concept = conceptService.load(codeSystem, code).orElseThrow();
    conceptService.cancel(concept.getCode(), codeSystem);
    provenanceService.create(CodeSystemProvenanceService.provenance("delete", concept));
    return HttpResponse.noContent();
  }

  @Authorized(Privilege.CS_VIEW)
  @Get(uri = "/{codeSystem}/versions/{version}/concepts/{code}")
  public Concept getConcept(@PathVariable String codeSystem, @PathVariable String version, @PathVariable String code) {
    return conceptService.load(codeSystem, version, parseCode(code))
        .orElseThrow(() -> new NotFoundException("Concept not found: " + parseCode(code)));
  }

  @Authorized(Privilege.CS_EDIT)
  @Post(uri = "/{codeSystem}/versions/{version}/concepts/transaction")
  public HttpResponse<?> saveConceptTransaction(@PathVariable String codeSystem, @PathVariable String version,
                                                @Body @Valid ConceptTransactionRequest request) {
    request.setCodeSystem(codeSystem);
    request.setCodeSystemVersion(version);
    Concept concept = provenanceService.provenanceConceptTransaction("save", request, () -> conceptService.save(request));
    return HttpResponse.created(concept);
  }

  @Authorized(Privilege.CS_EDIT)
  @Post(uri = "/{codeSystem}/versions/{version}/concepts/link")
  public HttpResponse<?> linkEntityVersion(@PathVariable String codeSystem, @PathVariable String version,
                                           @Body CodeSystemEntityVersionLinkRequest request) {
    provenanceService.provenanceCodeSystemVersion("link", codeSystem, version, () -> {
      codeSystemVersionService.linkEntityVersions(codeSystem, version, request.getEntityVersionIds());
    });
    return HttpResponse.ok();
  }

  @Authorized(Privilege.CS_EDIT)
  @Post(uri = "/{codeSystem}/versions/{version}/concepts/unlink")
  public HttpResponse<?> unlinkEntityVersion(@PathVariable String codeSystem, @PathVariable String version,
                                             @Body CodeSystemEntityVersionLinkRequest request) {
    provenanceService.provenanceCodeSystemVersion("unlink", codeSystem, version, () -> {
      codeSystemVersionService.unlinkEntityVersions(codeSystem, version, request.getEntityVersionIds());
    });
    return HttpResponse.ok();
  }

  @Authorized(Privilege.CS_VIEW)
  @Get(uri = "/{codeSystem}/versions/{version}/concepts/export{?params*}")
  public LorqueProcess exportConcepts(@PathVariable String codeSystem, @PathVariable String version, Map<String, String> params) {
    return conceptExportService.export(codeSystem, version, params.getOrDefault("format", "csv"));
  }

  @Authorized(Privilege.CS_VIEW)
  @Get(value = "/concepts/export-csv/result/{lorqueProcessId}", produces = "application/csv")
  public HttpResponse<?> getConceptExportCSV(Long lorqueProcessId) {
    MutableHttpResponse<byte[]> response = HttpResponse.ok(lorqueProcessService.load(lorqueProcessId).getResult());
    return response
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=concepts.csv")
        .contentType(MediaType.of("application/csv"));
  }

  @Authorized(Privilege.CS_VIEW)
  @Get(value = "/concepts/export-xlsx/result/{lorqueProcessId}", produces = "application/vnd.ms-excel")
  public HttpResponse<?> getConceptExportXLSX(Long lorqueProcessId) {
    MutableHttpResponse<byte[]> response = HttpResponse.ok(lorqueProcessService.load(lorqueProcessId).getResult());
    return response
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=concepts.xlsx")
        .contentType(MediaType.of("application/vnd.ms-excel"));
  }



  //----------------CodeSystem EntityVersion----------------

  @Authorized(Privilege.CS_VIEW)
  @Get(uri = "/{codeSystem}/entity-versions{?params*}")
  public QueryResult<CodeSystemEntityVersion> queryEntityVersions(@PathVariable String codeSystem, CodeSystemEntityVersionQueryParams params) {
    params.setCodeSystem(codeSystem);
    return codeSystemEntityVersionService.query(params);
  }

  @Authorized(Privilege.CS_PUBLISH)
  @Post(uri = "/{codeSystem}/entity-versions/{id}/activate")
  public HttpResponse<?> activateEntityVersion(@PathVariable String codeSystem, @PathVariable Long id) {
    codeSystemEntityVersionService.validate(codeSystem, id);
    provenanceService.provenanceEntityVersion("activate", id, () -> {
      codeSystemEntityVersionService.activate(id);
      return null;
    });
    return HttpResponse.noContent();
  }

  @Authorized(Privilege.CS_PUBLISH)
  @Post(uri = "/{codeSystem}/entity-versions/{id}/retire")
  public HttpResponse<?> retireEntityVersion(@PathVariable String codeSystem, @PathVariable Long id) {
    codeSystemEntityVersionService.validate(codeSystem, id);
    provenanceService.provenanceEntityVersion("retire", id, () -> {
      codeSystemEntityVersionService.retire(id);
      return null;
    });
    return HttpResponse.noContent();
  }

  @Authorized(Privilege.CS_PUBLISH)
  @Post(uri = "/{codeSystem}/entity-versions/{id}/draft")
  public HttpResponse<?> saveAsDraftEntityVersion(@PathVariable String codeSystem, @PathVariable Long id) {
    codeSystemEntityVersionService.validate(codeSystem, id);
    provenanceService.provenanceEntityVersion("save", id, () -> {
      codeSystemEntityVersionService.saveAsDraft(id);
      return null;
    });
    return HttpResponse.noContent();
  }

  @Authorized(Privilege.CS_EDIT)
  @Delete(uri = "/{codeSystem}/entity-versions/{id}")
  public HttpResponse<?> deleteEntityVersion(@PathVariable String codeSystem, @PathVariable Long id) {
    CodeSystemEntityVersion version = codeSystemEntityVersionService.load(id);
    codeSystemEntityVersionService.cancel(id);
    provenanceService.create(CodeSystemProvenanceService.provenance("delete", version));
    return HttpResponse.noContent();
  }

  @Authorized(Privilege.CS_EDIT)
  @Post(uri = "/{codeSystem}/entity-versions/{id}/duplicate")
  public HttpResponse<?> duplicateEntityVersion(@PathVariable String codeSystem, @PathVariable Long id) {
    CodeSystemEntityVersion newVersion = codeSystemEntityVersionService.duplicate(codeSystem, id);
    provenanceService.create(CodeSystemProvenanceService.provenance("duplicate", newVersion).created());
    return HttpResponse.ok();
  }

  @Authorized(Privilege.CS_EDIT)
  @Post(uri = "/{codeSystem}/entity-versions/supplement")
  public HttpResponse<?> supplementEntityVersion(@PathVariable String codeSystem, @Body CodeSystemConceptSupplementRequest request) {
    List<CodeSystemEntityVersion> versions = codeSystemSupplementService.supplement(codeSystem, null, request);
    versions.forEach(v -> provenanceService.create(CodeSystemProvenanceService.provenance("supplement", v).created()));
    return HttpResponse.ok();
  }

  @Authorized(Privilege.CS_EDIT)
  @Post(uri = "/{codeSystem}/versions/{version}/entity-versions/supplement")
  public HttpResponse<?> supplementEntityVersion(@PathVariable String codeSystem, @PathVariable String version, @Body CodeSystemConceptSupplementRequest request) {
    List<CodeSystemEntityVersion> versions = codeSystemSupplementService.supplement(codeSystem, version, request);
    versions.forEach(v -> provenanceService.create(CodeSystemProvenanceService.provenance("supplement", v).created()));
    return HttpResponse.ok();
  }

  @Authorized(Privilege.CS_VIEW)
  @Get(uri = "/{codeSystem}/entity-versions/{id}/references")
  public List<CodeSystemAssociation> getReferences(@PathVariable String codeSystem, @PathVariable Long id) {
    return codeSystemAssociationService.loadReferences(codeSystem, id);
  }


  //----------------CodeSystem Property----------------

  @Authorized(Privilege.CS_VIEW)
  @Get(uri = "/{codeSystem}/entity-properties{?params*}")
  public QueryResult<EntityProperty> queryEntityProperties(@PathVariable String codeSystem, EntityPropertyQueryParams params) {
    params.setCodeSystem(codeSystem);
    return entityPropertyService.query(params);
  }

  @Authorized(Privilege.CS_VIEW)
  @Get(uri = "/{codeSystem}/entity-properties/{id}")
  public EntityProperty getEntityProperty(@PathVariable String codeSystem, @PathVariable Long id) {
    return entityPropertyService.load(id)
        .orElseThrow(() -> new NotFoundException("EntityProperty not found: " + id));
  }

  @Authorized(Privilege.CS_EDIT)
  @Post(uri = "/{codeSystem}/entity-properties")
  public HttpResponse<?> createEntityProperty(@PathVariable String codeSystem, @Body @Valid EntityProperty property) {
    property.setId(null);
    provenanceService.provenanceCodeSystem("save", codeSystem, () -> {
      entityPropertyService.save(codeSystem, property);
    });
    return HttpResponse.created(property);
  }

  @Authorized(Privilege.CS_EDIT)
  @Put(uri = "/{codeSystem}/entity-properties/{id}")
  public HttpResponse<?> updateEntityProperty(@PathVariable String codeSystem, @PathVariable Long id, @Body @Valid EntityProperty property) {
    property.setId(id);
    provenanceService.provenanceCodeSystem("save", codeSystem, () -> {
      entityPropertyService.save(codeSystem, property);
    });
    return HttpResponse.created(property);
  }

  @Authorized(Privilege.CS_EDIT)
  @Delete(uri = "/{codeSystem}/entity-properties/{id}")
  public HttpResponse<?> deleteEntityProperty(@PathVariable String codeSystem, @PathVariable Long id) {
    provenanceService.provenanceCodeSystem("delete", codeSystem, () -> {
      entityPropertyService.cancel(codeSystem, id);
    });
    return HttpResponse.ok();
  }

  @Authorized(Privilege.CS_EDIT)
  @Post(uri = "/{codeSystem}/entity-properties/{propertyId}/delete-usages")
  public HttpResponse<?> deleteEntityPropertyUsages(@PathVariable String codeSystem, @PathVariable Long propertyId) {
    provenanceService.provenanceCodeSystem("delete-usages", codeSystem, () -> {
      entityPropertyService.deleteUsages(propertyId);
    });
    return HttpResponse.ok();
  }

  //----------------Provenances----------------

  @Authorized(Privilege.CS_VIEW)
  @Get(uri = "/{codeSystem}/provenances")
  public List<Provenance> queryProvenances(@PathVariable String codeSystem, @Nullable @QueryValue String version) {
    return provenanceService.find(codeSystem, version);
  }

  @Getter
  @Setter
  public static class CodeSystemDuplicateRequest {
    private String codeSystem;
    private String codeSystemUri;
  }

  @Getter
  @Setter
  public static class CodeSystemVersionDuplicateRequest {
    private String codeSystem;
    private String version;
  }

  @Getter
  @Setter
  public static class CodeSystemEntityVersionLinkRequest {
    private List<Long> entityVersionIds;
  }

  @Getter
  @Setter
  public static class CodeSystemConceptPropertyPropagationRequest {
    private List<Long> targetConceptIds;
  }

  private String parseCode(String code) {
    return URLDecoder.decode(code, StandardCharsets.UTF_8);
  }
}
