package com.kodality.termserver.fhir.codesystem;

import com.kodality.commons.model.QueryResult;
import com.kodality.kefhir.core.model.ResourceId;
import com.kodality.kefhir.core.model.ResourceVersion;
import com.kodality.kefhir.core.model.VersionId;
import com.kodality.kefhir.core.model.search.SearchCriterion;
import com.kodality.kefhir.core.model.search.SearchResult;
import com.kodality.kefhir.structure.api.ResourceContent;
import com.kodality.termserver.fhir.BaseFhirResourceStorage;
import com.kodality.termserver.terminology.codesystem.CodeSystemImportService;
import com.kodality.termserver.terminology.codesystem.CodeSystemService;
import com.kodality.termserver.terminology.codesystem.CodeSystemVersionService;
import com.kodality.termserver.terminology.codesystem.entity.CodeSystemEntityVersionService;
import com.kodality.termserver.ts.PublicationStatus;
import com.kodality.termserver.ts.association.AssociationKind;
import com.kodality.termserver.ts.association.AssociationType;
import com.kodality.termserver.ts.codesystem.CodeSystem;
import com.kodality.termserver.ts.codesystem.CodeSystemEntityVersionQueryParams;
import com.kodality.termserver.ts.codesystem.CodeSystemQueryParams;
import com.kodality.termserver.ts.codesystem.CodeSystemVersion;
import com.kodality.zmei.fhir.FhirMapper;
import jakarta.inject.Singleton;
import java.util.List;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class CodeSystemResourceStorage extends BaseFhirResourceStorage {
  private final CodeSystemService codeSystemService;
  private final CodeSystemVersionService codeSystemVersionService;
  private final CodeSystemEntityVersionService codeSystemEntityVersionService;
  private final CodeSystemFhirMapper mapper;
  private final CodeSystemImportService importService;

  @Override
  public String getResourceType() {
    return "CodeSystem";
  }

  @Override
  public ResourceVersion load(String codeSystemId) {
    CodeSystemQueryParams codeSystemParams = new CodeSystemQueryParams();
    codeSystemParams.setId(codeSystemId);
    codeSystemParams.setPropertiesDecorated(true);
    codeSystemParams.setLimit(1);
    CodeSystem codeSystem = codeSystemService.query(codeSystemParams).findFirst().orElse(null);
    if (codeSystem == null) {
      return null;
    }
    CodeSystemVersion version = codeSystemVersionService.loadLastVersion(codeSystemId);
    CodeSystemEntityVersionQueryParams codeSystemEntityVersionParams = new CodeSystemEntityVersionQueryParams()
        .setCodeSystemVersionId(version.getId())
        .all();
    Integer count = codeSystemEntityVersionService.count(codeSystemEntityVersionParams);
    if (count < 1000) {
      version.setEntities(codeSystemEntityVersionService.query(codeSystemEntityVersionParams).getData());
    } else {
      version.setEntities(List.of());
    }
    return toFhir(codeSystem, version);
  }

  @Override
  public ResourceVersion save(ResourceId id, ResourceContent content) {
    com.kodality.zmei.fhir.resource.terminology.CodeSystem codeSystem =
        FhirMapper.fromJson(content.getValue(), com.kodality.zmei.fhir.resource.terminology.CodeSystem.class);
    List<AssociationType> associationTypes = List.of(new AssociationType("is-a", AssociationKind.codesystemHierarchyMeaning, true));
    importService.importCodeSystem(CodeSystemFhirImportMapper.mapCodeSystem(codeSystem), associationTypes,
        PublicationStatus.active.equals(codeSystem.getStatus()));
    return load(id.getResourceId());
  }

  @Override
  public String generateNewId() {
    return null; //TODO:
  }

  @Override
  public SearchResult search(SearchCriterion criteria) {
    QueryResult<CodeSystem> result = codeSystemService.query(mapper.fromFhir(criteria));
    String code = criteria.getRawParams().containsKey("code") ? criteria.getRawParams().get("code").get(0) : null;
    return new SearchResult(result.getMeta().getTotal(), result.getData().stream().flatMap(cs -> cs.getVersions().stream().map(csv -> {
      CodeSystemEntityVersionQueryParams codeSystemEntityVersionParams = new CodeSystemEntityVersionQueryParams()
          .setCodeSystemVersionId(csv.getId())
          .setCode(code)
          .all();
      Integer count = codeSystemEntityVersionService.count(codeSystemEntityVersionParams);
      if (count < 1000) {
        csv.setEntities(codeSystemEntityVersionService.query(codeSystemEntityVersionParams).getData());
      } else {
        csv.setEntities(List.of());
      }
      return toFhir(cs, csv);
    })).toList());
  }

  private ResourceVersion toFhir(CodeSystem cs, CodeSystemVersion csv) {
    return cs == null ? null :
        new ResourceVersion(new VersionId("CodeSystem", cs.getId().toString()), new ResourceContent(mapper.toFhirJson(cs, csv), "json"));
  }

}
