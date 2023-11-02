package com.kodality.termx.terminology.fhir.valueset;

import com.kodality.commons.model.QueryResult;
import com.kodality.kefhir.core.exception.FhirException;
import com.kodality.kefhir.core.model.ResourceId;
import com.kodality.kefhir.core.model.ResourceVersion;
import com.kodality.kefhir.core.model.VersionId;
import com.kodality.kefhir.core.model.search.SearchCriterion;
import com.kodality.kefhir.core.model.search.SearchResult;
import com.kodality.kefhir.structure.api.ResourceContent;
import com.kodality.termx.core.fhir.BaseFhirResourceStorage;
import com.kodality.termx.core.sys.provenance.Provenance;
import com.kodality.termx.core.sys.provenance.ProvenanceService;
import com.kodality.termx.terminology.terminology.valueset.ValueSetService;
import com.kodality.termx.terminology.terminology.valueset.ValueSetVersionService;
import com.kodality.termx.ts.valueset.ValueSet;
import com.kodality.termx.ts.valueset.ValueSetQueryParams;
import com.kodality.termx.ts.valueset.ValueSetVersion;
import com.kodality.zmei.fhir.FhirMapper;
import jakarta.inject.Singleton;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hl7.fhir.r5.model.OperationOutcome.IssueType;

@Singleton
@RequiredArgsConstructor
public class ValueSetResourceStorage extends BaseFhirResourceStorage {
  private final ValueSetService valueSetService;
  private final ValueSetVersionService valueSetVersionService;
  private final ValueSetFhirImportService importService;
  private final ProvenanceService provenanceService;

  @Override
  public String getResourceType() {
    return "ValueSet";
  }

  @Override
  public ResourceVersion load(String fhirId) {
    String[] idParts = ValueSetFhirMapper.parseCompositeId(fhirId);
    String vsId = idParts[0];
    String versionNumber = idParts[1];

    return load(vsId, versionNumber);
  }

  private ResourceVersion load(String vsId, String versionNumber) {
    ValueSetQueryParams valueSetParams = new ValueSetQueryParams();
    valueSetParams.setId(vsId);
    valueSetParams.setLimit(1);
    ValueSet valueSet = valueSetService.query(valueSetParams).findFirst().orElse(null);
    if (valueSet == null) {
      return null;
    }
    ValueSetVersion version = versionNumber == null ? valueSetVersionService.loadLastVersion(vsId) :
        valueSetVersionService.load(vsId, versionNumber).orElseThrow(() -> new FhirException(400, IssueType.NOTFOUND, "resource not found"));
    return toFhir(valueSet, version);
  }

  @Override
  public ResourceVersion save(ResourceId id, ResourceContent content) {
    com.kodality.zmei.fhir.resource.terminology.ValueSet valueSet =
        FhirMapper.fromJson(content.getValue(), com.kodality.zmei.fhir.resource.terminology.ValueSet.class);
    importService.importValueSet(valueSet);
    return load(valueSet.getId(), valueSet.getVersion());
  }

  @Override
  public String generateNewId() {
    return null; //TODO:
  }

  @Override
  public SearchResult search(SearchCriterion criteria) {
    QueryResult<ValueSet> vsResult = valueSetService.query(ValueSetFhirMapper.fromFhir(criteria));
    QueryResult<ValueSetVersion> vsvResult = valueSetVersionService.query(ValueSetFhirMapper.fromFhirVSVersionParams(criteria).limit(0));
    return new SearchResult(vsvResult.getMeta().getTotal(),
        vsResult.getData().stream().flatMap(vs -> vs.getVersions().stream().map(vsv -> toFhir(vs, vsv))).toList());
  }

  private ResourceVersion toFhir(ValueSet vs, ValueSetVersion vsv) {
    List<Provenance> provenances = provenanceService.find("ValueSetVersion|" + vsv.getId());
    return vs == null ? null : new ResourceVersion(
        new VersionId("ValueSet", ValueSetFhirMapper.toFhirId(vs, vsv)),
        new ResourceContent(ValueSetFhirMapper.toFhirJson(vs, vsv, provenances), "json")
    );
  }

}
