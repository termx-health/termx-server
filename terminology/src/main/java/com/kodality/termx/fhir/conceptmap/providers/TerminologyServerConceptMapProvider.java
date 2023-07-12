package com.kodality.termx.fhir.conceptmap.providers;

import com.kodality.termx.fhir.conceptmap.ConceptMapFhirImportService;
import com.kodality.termx.sys.ResourceType;
import com.kodality.termx.sys.server.resource.TerminologyServerResourceProvider;
import com.kodality.termx.sys.server.resource.TerminologyServerResourceSyncProvider;
import com.kodality.termx.terminology.FhirClient;
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