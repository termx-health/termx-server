package com.kodality.termx.core.ts;


import com.kodality.zmei.fhir.resource.terminology.CodeSystem;

public abstract class CodeSystemFhirImportProvider {

  public abstract void importCodeSystem(CodeSystem codeSystem);
}
