package com.kodality.termx.terminology.fhir.conceptmap.providers;

import com.kodality.termx.sys.server.resource.TerminologyServerResourceProvider;
import com.kodality.termx.sys.server.resource.TerminologyServerResourceSyncProvider;
import com.kodality.termx.terminology.terminology.FhirServerHttpClientService;
import com.kodality.zmei.fhir.client.FhirClient;
import com.kodality.zmei.fhir.resource.ResourceType;
import com.kodality.zmei.fhir.resource.terminology.ConceptMap;
import com.kodality.zmei.fhir.search.FhirQueryParams;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class TerminologyServerConceptMapProvider implements TerminologyServerResourceProvider, TerminologyServerResourceSyncProvider {
  private final FhirServerHttpClientService fhirClientService;

  @Override
  public String getType() {
    return com.kodality.termx.sys.ResourceType.mapSet;
  }

  @Override
  public Object getResource(Long serverId, String resourceId) {
    FhirClient client = fhirClientService.getHttpClient(serverId);
    return client.read(ResourceType.conceptMap, resourceId).join();
  }

  @Override
  public void sync(Long sourceServerId, Long targetServerId, String resourceId, boolean clearSync) {
    FhirClient sourceClient = fhirClientService.getHttpClient(sourceServerId);
    FhirClient targetClient = fhirClientService.getHttpClient(targetServerId);

    ConceptMap conceptMap = sourceClient.<ConceptMap>read("ConceptMap", resourceId).join();
    if (clearSync) {
      targetClient.search(ResourceType.conceptMap, new FhirQueryParams(Map.of("url", List.of(conceptMap.getUrl())))).join().getEntry().forEach(e -> {
        targetClient.delete(ResourceType.conceptMap, ((ConceptMap) e.getResource()).getId()).join();
      });
    }
    targetClient.update(conceptMap.getId(), conceptMap).join();
  }
}
