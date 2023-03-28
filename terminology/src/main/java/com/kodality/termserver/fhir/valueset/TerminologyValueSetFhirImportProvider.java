package com.kodality.termserver.fhir.valueset;

import com.kodality.termserver.ts.ValueSetFhirImportProvider;
import com.kodality.zmei.fhir.resource.terminology.ValueSet;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class TerminologyValueSetFhirImportProvider extends ValueSetFhirImportProvider {
  private final ValueSetFhirImportService fhirImportService;

  @Override
  public void importValueSet(ValueSet valueSet) {
    fhirImportService.importValueSet(valueSet, true);
  }
}
