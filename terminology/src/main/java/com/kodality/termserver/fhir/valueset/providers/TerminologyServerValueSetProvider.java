package com.kodality.termserver.fhir.valueset.providers;

import com.kodality.termserver.fhir.valueset.ValueSetFhirImportService;
import com.kodality.termserver.sys.ResourceType;
import com.kodality.termserver.sys.server.resource.TerminologyServerResourceProvider;
import com.kodality.termserver.sys.server.resource.TerminologyServerResourceSyncProvider;
import com.kodality.termserver.terminology.FhirClient;
import com.kodality.zmei.fhir.resource.terminology.ValueSet;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class TerminologyServerValueSetProvider implements TerminologyServerResourceProvider, TerminologyServerResourceSyncProvider {
  private final ValueSetFhirImportService importService;
  @Override
  public String getType() {
    return ResourceType.valueSet;
  }

  @Override
  public Object getResource(String serverRootUrl, String resourceId) {
    FhirClient client = new FhirClient(serverRootUrl + "/fhir");
    return client.<ValueSet>read("ValueSet", resourceId).join();
  }

  @Override
  public void syncFrom(String serverRootUrl, String resourceId) {
    FhirClient client = new FhirClient(serverRootUrl + "/fhir");
    ValueSet valueSet = client.<ValueSet>read("ValueSet", resourceId).join();
    importService.importValueSet(valueSet, false);
  }
}
