package com.kodality.termserver.fhir.codesystem;

import com.kodality.termserver.ts.CodeSystemFhirImportProvider;
import com.kodality.zmei.fhir.resource.terminology.CodeSystem;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class TerminologyCodeSystemFhirImportProvider extends CodeSystemFhirImportProvider {
  private final CodeSystemFhirImportService fhirImportService;

  @Override
  public void importCodeSystem(CodeSystem codeSystem) {
    fhirImportService.importCodeSystem(codeSystem);
  }
}
