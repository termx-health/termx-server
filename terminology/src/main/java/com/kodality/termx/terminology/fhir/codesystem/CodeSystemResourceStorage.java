package com.kodality.termx.terminology.fhir.codesystem;

import com.kodality.commons.model.QueryResult;
import com.kodality.kefhir.core.exception.FhirException;
import com.kodality.kefhir.core.model.ResourceId;
import com.kodality.kefhir.core.model.ResourceVersion;
import com.kodality.kefhir.core.model.VersionId;
import com.kodality.kefhir.core.model.search.SearchCriterion;
import com.kodality.kefhir.core.model.search.SearchResult;
import com.kodality.kefhir.structure.api.ResourceContent;
import com.kodality.termx.core.fhir.BaseFhirResourceHandler;
import com.kodality.termx.core.sys.provenance.Provenance;
import com.kodality.termx.core.sys.provenance.ProvenanceService;
import com.kodality.termx.terminology.terminology.codesystem.CodeSystemImportService;
import com.kodality.termx.terminology.terminology.codesystem.CodeSystemService;
import com.kodality.termx.terminology.terminology.codesystem.entity.CodeSystemEntityVersionService;
import com.kodality.termx.terminology.terminology.codesystem.version.CodeSystemVersionService;
import com.kodality.termx.ts.PublicationStatus;
import com.kodality.termx.ts.association.AssociationKind;
import com.kodality.termx.ts.association.AssociationType;
import com.kodality.termx.ts.codesystem.CodeSystem;
import com.kodality.termx.ts.codesystem.CodeSystemEntityVersion;
import com.kodality.termx.ts.codesystem.CodeSystemEntityVersionQueryParams;
import com.kodality.termx.ts.codesystem.CodeSystemImportAction;
import com.kodality.termx.ts.codesystem.CodeSystemQueryParams;
import com.kodality.termx.ts.codesystem.CodeSystemVersion;
import com.kodality.zmei.fhir.FhirMapper;
import jakarta.inject.Singleton;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r5.model.OperationOutcome.IssueType;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class CodeSystemResourceStorage extends BaseFhirResourceHandler {
  private final CodeSystemService codeSystemService;
  private final CodeSystemVersionService codeSystemVersionService;
  private final CodeSystemEntityVersionService codeSystemEntityVersionService;
  private final ProvenanceService provenanceService;
  private final CodeSystemImportService importService;

  @Override
  public String getResourceType() {
    return "CodeSystem";
  }

  @Override
  public String getPrivilegeName() {
    return "CodeSystem";
  }

  @Override
  public ResourceVersion load(String fhirId) {
    String[] idParts = CodeSystemFhirMapper.parseCompositeId(fhirId);
    return load(idParts[0], idParts[1]);
  }

  private ResourceVersion load(String codeSystemId, String versionNumber) {
    CodeSystemQueryParams codeSystemParams = new CodeSystemQueryParams();
    codeSystemParams.setId(codeSystemId);
    codeSystemParams.setPropertiesDecorated(true);
    codeSystemParams.setLimit(1);
    CodeSystem codeSystem = codeSystemService.query(codeSystemParams).findFirst().orElse(null);
    if (codeSystem == null) {
      return null;
    }

    long start = System.currentTimeMillis();
    CodeSystemVersion version = versionNumber == null ? codeSystemVersionService.loadLastVersion(codeSystemId) :
        codeSystemVersionService.load(codeSystemId, versionNumber).orElseThrow(() -> new FhirException(400, IssueType.NOTFOUND, "resource not found"));
    log.info("Code System load took " + (System.currentTimeMillis() - start) / 1000 + " seconds");
    start = System.currentTimeMillis();

    version.setEntities(loadEntities(version, null, true));
    log.info("Entities load took " + (System.currentTimeMillis() - start) / 1000 + " seconds");
    start = System.currentTimeMillis();

    ResourceVersion resourceVersion = toFhir(codeSystem, version);
    log.info("To FHIR conversion took " + (System.currentTimeMillis() - start) / 1000 + " seconds");

    return resourceVersion;
  }

  @Override
  public ResourceVersion save(ResourceId id, ResourceContent content) {
    com.kodality.zmei.fhir.resource.terminology.CodeSystem codeSystem =
        FhirMapper.fromJson(content.getValue(), com.kodality.zmei.fhir.resource.terminology.CodeSystem.class);
    List<AssociationType> associationTypes = List.of(new AssociationType("is-a", AssociationKind.codesystemHierarchyMeaning, true));
    CodeSystemImportAction action = new CodeSystemImportAction().setActivate(PublicationStatus.active.equals(codeSystem.getStatus()));
    CodeSystem cs = importService.importCodeSystem(CodeSystemFhirMapper.fromFhirCodeSystem(codeSystem), associationTypes, action);
    return load(cs.getId(), cs.getVersions().get(0).getVersion());
  }

  @Override
  public String generateNewId() {
    return null; //TODO:
  }

  @Override
  public SearchResult search(SearchCriterion criteria) {
    QueryResult<CodeSystem> result = codeSystemService.query(CodeSystemFhirMapper.fromFhir(criteria));
    String code = criteria.getRawParams().containsKey("code") ? criteria.getRawParams().get("code").get(0) : null;
    return new SearchResult(result.getMeta().getTotal(), result.getData().stream().flatMap(cs -> cs.getVersions().stream().map(csv -> {
      csv.setEntities(loadEntities(csv, code, false));
      return toFhir(cs, csv);
    })).toList());
  }

  private List<CodeSystemEntityVersion> loadEntities(CodeSystemVersion version, String code, boolean loadLargeEntities) {
    if (version == null || (version.getConceptsTotal() > 1000 && !loadLargeEntities)) {
      return List.of();
    }
    CodeSystemEntityVersionQueryParams codeSystemEntityVersionParams = new CodeSystemEntityVersionQueryParams()
        .setCodeSystemVersionId(version.getId())
        .setCode(code)
        .all();
    return codeSystemEntityVersionService.query(codeSystemEntityVersionParams).getData();
  }

  private ResourceVersion toFhir(CodeSystem cs, CodeSystemVersion csv) {
    List<Provenance> provenances = provenanceService.find("CodeSystemVersion|" + csv.getId());
    return cs == null ? null : new ResourceVersion(
        new VersionId("CodeSystem", CodeSystemFhirMapper.toFhirId(cs, csv)),
        new ResourceContent(CodeSystemFhirMapper.toFhirJson(cs, csv, provenances), "json")
    );
  }

}
