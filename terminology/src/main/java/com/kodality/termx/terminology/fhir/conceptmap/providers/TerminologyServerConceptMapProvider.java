package com.kodality.termx.terminology.fhir.conceptmap.providers;

import com.kodality.termx.sys.ResourceType;
import com.kodality.termx.sys.server.resource.TerminologyServerResourceProvider;
import com.kodality.termx.sys.server.resource.TerminologyServerResourceSyncProvider;
import com.kodality.termx.terminology.terminology.FhirServerHttpClientService;
import com.kodality.zmei.fhir.client.FhirClient;
import com.kodality.zmei.fhir.client.FhirClientError;
import com.kodality.zmei.fhir.resource.terminology.ConceptMap;
import jakarta.inject.Singleton;
import java.util.concurrent.CompletionException;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class TerminologyServerConceptMapProvider implements TerminologyServerResourceProvider, TerminologyServerResourceSyncProvider {
  private final FhirServerHttpClientService fhirClientService;

  @Override
  public String getType() {
    return ResourceType.mapSet;
  }

  @Override
  public Object getResource(Long serverId, String resourceId) {
    FhirClient client = fhirClientService.getHttpClient(serverId);
    return client.read("ConceptMap", resourceId).join();
  }

  @Override
  public void sync(Long sourceServerId, Long targetServerId, String resourceId) {
    FhirClient sourceClient = fhirClientService.getHttpClient(sourceServerId);
    FhirClient targetClient = fhirClientService.getHttpClient(targetServerId);

    ConceptMap conceptMap = sourceClient.<ConceptMap>read("ConceptMap", resourceId).join();

    try {
      targetClient.<ConceptMap>read("ConceptMap", resourceId).join();
      targetClient.update(resourceId, conceptMap).join();
    } catch (CompletionException e)  {
      if (e.getCause() instanceof FhirClientError && ((FhirClientError) e.getCause()).getResponse().statusCode() == 404) {
        targetClient.create(conceptMap).join();
      }
    }
  }
}
