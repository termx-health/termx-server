package com.kodality.termserver.fhir.valueset;

import com.kodality.termserver.ts.valueset.ValueSetService;
import com.kodality.termserver.ts.valueset.ValueSetVersionService;
import com.kodality.termserver.valueset.ValueSet;
import com.kodality.termserver.valueset.ValueSetConcept;
import com.kodality.termserver.valueset.ValueSetQueryParams;
import com.kodality.termserver.valueset.ValueSetVersion;
import com.kodality.termserver.valueset.ValueSetVersionQueryParams;
import com.kodality.zmei.fhir.search.FhirQueryParams;
import java.util.List;
import java.util.Map;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class ValueSetFhirService {
  private final ValueSetFhirMapper mapper;
  private final ValueSetService valueSetService;
  private final ValueSetVersionService valueSetVersionService;

  public com.kodality.zmei.fhir.resource.terminology.ValueSet get(Long valueSetVersionId) {
    ValueSet valueSet = valueSetService.query(new ValueSetQueryParams().setVersionId(valueSetVersionId)).findFirst().orElse(null);
    if (valueSet == null) {
      return null;
    }
    ValueSetVersion version = valueSetVersionService.load(valueSetVersionId);
    return mapper.toFhir(valueSet, version);
  }

  public com.kodality.zmei.fhir.resource.terminology.ValueSet expand(Map<String, List<String>> params) {
    FhirQueryParams fhirParams = new FhirQueryParams(params);

    if (fhirParams.getFirst("url").isEmpty() || fhirParams.getFirst("valueSetVersion").isEmpty()) {
      return null;
    }
    ValueSetVersionQueryParams vsvParams = new ValueSetVersionQueryParams();
    vsvParams.setValueSetUri(fhirParams.getFirst("url").get());
    vsvParams.setVersion(fhirParams.getFirst("valueSetVersion").get());
    vsvParams.setDecorated(true);
    vsvParams.setLimit(1);
    ValueSetVersion version = valueSetVersionService.query(vsvParams).findFirst().orElse(null);
    if (version == null) {
      return null;
    }

    ValueSetQueryParams vsParams = new ValueSetQueryParams();
    vsParams.setVersionId(version.getId());
    vsParams.setLimit(1);
    ValueSet valueSet = valueSetService.query(vsParams).findFirst().orElse(null);
    if (valueSet == null) {
      return null;
    }

    List<ValueSetConcept> expandedConcepts = valueSetVersionService.expand(valueSet.getId(), version.getVersion(), null);
    return mapper.toFhir(valueSet, version, expandedConcepts);
  }

}
