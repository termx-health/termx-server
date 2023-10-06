package com.kodality.termx.fhir.valueset.providers;

import com.kodality.termx.fhir.valueset.ValueSetFhirImportService;
import com.kodality.termx.sys.ResourceType;
import com.kodality.termx.sys.server.resource.TerminologyServerResourceProvider;
import com.kodality.termx.sys.server.resource.TerminologyServerResourceSyncProvider;
import com.kodality.termx.terminology.TerminologyServerFhirClientService;
import com.kodality.zmei.fhir.client.FhirClient;
import com.kodality.zmei.fhir.resource.terminology.ValueSet;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class TerminologyServerValueSetProvider implements TerminologyServerResourceProvider, TerminologyServerResourceSyncProvider {
  private final TerminologyServerFhirClientService fhirClientService;
  private final ValueSetFhirImportService importService;

  @Override
  public String getType() {
    return ResourceType.valueSet;
  }

  @Override
  public Object getResource(Long serverId, String resourceId) {
    FhirClient client = fhirClientService.getFhirClient(serverId).join();
    return client.read("ValueSet", resourceId).join();
  }

  @Override
  public void syncFrom(Long serverId, String resourceId) {
    FhirClient client = fhirClientService.getFhirClient(serverId).join();
    ValueSet valueSet = client.<ValueSet>read("ValueSet", resourceId).join();
    importService.importValueSet(valueSet);
  }
}
