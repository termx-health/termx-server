package com.kodality.termx.fhir.conceptmap.providers;

import com.kodality.termx.fhir.conceptmap.ConceptMapFhirImportService;
import com.kodality.termx.sys.ResourceType;
import com.kodality.termx.sys.server.resource.TerminologyServerResourceProvider;
import com.kodality.termx.sys.server.resource.TerminologyServerResourceSyncProvider;
import com.kodality.termx.terminology.TerminologyServerFhirClientService;
import com.kodality.termx.ts.mapset.MapSetImportAction;
import com.kodality.zmei.fhir.client.FhirClient;
import com.kodality.zmei.fhir.resource.terminology.ConceptMap;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class TerminologyServerConceptMapProvider implements TerminologyServerResourceProvider, TerminologyServerResourceSyncProvider {
  private final TerminologyServerFhirClientService fhirClientService;
  private final ConceptMapFhirImportService importService;

  @Override
  public String getType() {
    return ResourceType.mapSet;
  }

  @Override
  public Object getResource(Long serverId, String resourceId) {
    FhirClient client = fhirClientService.getFhirClient(serverId).join();
    return client.read("ConceptMap", resourceId).join();
  }

  @Override
  public void syncFrom(Long serverId, String resourceId) {
    FhirClient client = fhirClientService.getFhirClient(serverId).join();
    ConceptMap conceptMap = client.<ConceptMap>read("ConceptMap", resourceId).join();
    importService.importMapSet(conceptMap, new MapSetImportAction());
  }
}
