package com.kodality.termserver.ts;


import com.kodality.zmei.fhir.resource.terminology.ValueSet;

public abstract class ValueSetFhirImportProvider {

  public abstract void importValueSet(ValueSet valueSet);
}
