package com.kodality.termserver.fhir.valueset;

import com.kodality.termserver.ts.valueset.ValueSetService;
import com.kodality.termserver.ts.valueset.ValueSetVersionService;
import com.kodality.termserver.valueset.ValueSet;
import com.kodality.termserver.valueset.ValueSetQueryParams;
import com.kodality.termserver.valueset.ValueSetVersion;
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
    ValueSetVersion version = valueSetVersionService.getVersion(valueSetVersionId);
    return mapper.toFhir(valueSet, version);
  }
}
