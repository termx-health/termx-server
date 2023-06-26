package com.kodality.termserver.fhir.conceptmap.providers;

import com.kodality.termserver.fhir.conceptmap.ConceptMapFhirImportService;
import com.kodality.termserver.sys.ResourceType;
import com.kodality.termserver.sys.server.resource.TerminologyServerResourceProvider;
import com.kodality.termserver.sys.server.resource.TerminologyServerResourceSyncProvider;
import com.kodality.termserver.terminology.FhirClient;
import com.kodality.zmei.fhir.resource.terminology.ConceptMap;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class TerminologyServerConceptMapProvider implements TerminologyServerResourceProvider, TerminologyServerResourceSyncProvider {
  private final ConceptMapFhirImportService importService;

  @Override
  public String getType() {
    return ResourceType.mapSet;
  }

  @Override
  public Object getResource(String serverRootUrl, String resourceId) {
    FhirClient client = new FhirClient(serverRootUrl + "/fhir");
    return client.<ConceptMap>read("ConceptMap", resourceId).join();
  }

  @Override
  public void syncFrom(String serverRootUrl, String resourceId) {
    FhirClient client = new FhirClient(serverRootUrl + "/fhir");
    ConceptMap conceptMap = client.<ConceptMap>read("ConceptMap", resourceId).join();
    importService.importMapSet(conceptMap);
  }
}
