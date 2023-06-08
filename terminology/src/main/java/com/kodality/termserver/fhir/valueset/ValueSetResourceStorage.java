package com.kodality.termserver.fhir.valueset;

import com.kodality.commons.model.QueryResult;
import com.kodality.kefhir.core.model.ResourceId;
import com.kodality.kefhir.core.model.ResourceVersion;
import com.kodality.kefhir.core.model.VersionId;
import com.kodality.kefhir.core.model.search.SearchCriterion;
import com.kodality.kefhir.core.model.search.SearchResult;
import com.kodality.kefhir.structure.api.ResourceContent;
import com.kodality.termserver.fhir.BaseFhirResourceStorage;
import com.kodality.termserver.terminology.valueset.ValueSetService;
import com.kodality.termserver.terminology.valueset.ValueSetVersionService;
import com.kodality.termserver.ts.valueset.ValueSet;
import com.kodality.termserver.ts.valueset.ValueSetQueryParams;
import com.kodality.termserver.ts.valueset.ValueSetVersion;
import com.kodality.zmei.fhir.FhirMapper;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class ValueSetResourceStorage extends BaseFhirResourceStorage {
  private final ValueSetService valueSetService;
  private final ValueSetVersionService valueSetVersionService;
  private final ValueSetFhirMapper mapper;
  private final ValueSetFhirImportService importService;

  @Override
  public String getResourceType() {
    return "ValueSet";
  }

  @Override
  public ResourceVersion load(String id) {
    ValueSetQueryParams valueSetParams = new ValueSetQueryParams();
    valueSetParams.setId(id);
    valueSetParams.setLimit(1);
    ValueSet valueSet = valueSetService.query(valueSetParams).findFirst().orElse(null);
    if (valueSet == null) {
      return null;
    }
    ValueSetVersion version = valueSetVersionService.loadLastVersion(id);
    return toFhir(valueSet, version);
  }

  @Override
  public ResourceVersion save(ResourceId id, ResourceContent content) {
    com.kodality.zmei.fhir.resource.terminology.ValueSet valueSet =
        FhirMapper.fromJson(content.getValue(), com.kodality.zmei.fhir.resource.terminology.ValueSet.class);
    importService.importValueSet(valueSet, false);
    return load(id.getResourceId());
  }

  @Override
  public String generateNewId() {
    return null; //TODO:
  }

  @Override
  public SearchResult search(SearchCriterion criteria) {
    QueryResult<ValueSet> result = valueSetService.query(mapper.fromFhir(criteria));
    return new SearchResult(result.getMeta().getTotal(),
        result.getData().stream().flatMap(cs -> cs.getVersions().stream().map(csv -> toFhir(cs, csv))).toList());
  }

  private ResourceVersion toFhir(ValueSet valueSet, ValueSetVersion version) {
    return valueSet == null ? null :
        new ResourceVersion(new VersionId("ValueSet", valueSet.getId()), new ResourceContent(mapper.toFhirJson(valueSet, version), "json"));
  }

}
