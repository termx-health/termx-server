package com.kodality.termserver.fhir.codesystem.providers;

import com.kodality.termserver.fhir.codesystem.CodeSystemFhirImportService;
import com.kodality.termserver.sys.ResourceType;
import com.kodality.termserver.sys.server.resource.TerminologyServerResourceProvider;
import com.kodality.termserver.sys.server.resource.TerminologyServerResourceSyncProvider;
import com.kodality.termserver.terminology.FhirClient;
import com.kodality.zmei.fhir.resource.terminology.CodeSystem;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class TerminologyServerCodeSystemProvider implements TerminologyServerResourceProvider, TerminologyServerResourceSyncProvider {
  private final CodeSystemFhirImportService importService;

  @Override
  public String getType() {
    return ResourceType.codeSystem;
  }

  @Override
  public Object getResource(String serverRootUrl, String resourceId) {
    FhirClient client = new FhirClient(serverRootUrl + "/fhir");
    return client.<CodeSystem>read("CodeSystem", resourceId).join();
  }

  @Override
  public void syncFrom(String serverRootUrl, String resourceId) {
    FhirClient client = new FhirClient(serverRootUrl + "/fhir");
    CodeSystem codeSystem = client.<CodeSystem>read("CodeSystem", resourceId).join();
    importService.importCodeSystem(codeSystem);
  }
}
