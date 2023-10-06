package com.kodality.termx.fhir.codesystem.providers;

import com.kodality.termx.fhir.codesystem.CodeSystemFhirImportService;
import com.kodality.termx.sys.ResourceType;
import com.kodality.termx.sys.server.resource.TerminologyServerResourceProvider;
import com.kodality.termx.sys.server.resource.TerminologyServerResourceSyncProvider;
import com.kodality.termx.terminology.TerminologyServerFhirClientService;
import com.kodality.zmei.fhir.client.FhirClient;
import com.kodality.zmei.fhir.resource.terminology.CodeSystem;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class TerminologyServerCodeSystemProvider implements TerminologyServerResourceProvider, TerminologyServerResourceSyncProvider {
  private final TerminologyServerFhirClientService fhirClientService;
  private final CodeSystemFhirImportService importService;

  @Override
  public String getType() {
    return ResourceType.codeSystem;
  }

  @Override
  public Object getResource(Long serverId, String resourceId) {
    FhirClient client = fhirClientService.getFhirClient(serverId).join();
    return client.read("CodeSystem", resourceId).join();
  }

  @Override
  public void syncFrom(Long serverId, String resourceId) {
    FhirClient client = fhirClientService.getFhirClient(serverId).join();
    CodeSystem codeSystem = client.<CodeSystem>read("CodeSystem", resourceId).join();
    importService.importCodeSystem(codeSystem);
  }
}
