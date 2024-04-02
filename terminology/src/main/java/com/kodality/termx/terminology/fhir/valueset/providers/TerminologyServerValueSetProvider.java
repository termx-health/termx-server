package com.kodality.termx.terminology.fhir.valueset.providers;


import com.kodality.termx.sys.server.resource.TerminologyServerResourceProvider;
import com.kodality.termx.sys.server.resource.TerminologyServerResourceSyncProvider;
import com.kodality.termx.terminology.terminology.FhirServerHttpClientService;
import com.kodality.zmei.fhir.client.FhirClient;
import com.kodality.zmei.fhir.resource.ResourceType;
import com.kodality.zmei.fhir.resource.terminology.ValueSet;
import com.kodality.zmei.fhir.search.FhirQueryParams;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class TerminologyServerValueSetProvider implements TerminologyServerResourceProvider, TerminologyServerResourceSyncProvider {
  private final FhirServerHttpClientService fhirClientService;

  @Override
  public String getType() {
    return com.kodality.termx.sys.ResourceType.valueSet;
  }

  @Override
  public Object getResource(Long serverId, String resourceId) {
    FhirClient client = fhirClientService.getHttpClient(serverId);
    return client.read(ResourceType.valueSet, resourceId).join();
  }

  @Override
  public void sync(Long sourceServerId, Long targetServerId, String resourceId, boolean clearSync) {
    FhirClient sourceClient = fhirClientService.getHttpClient(sourceServerId);
    FhirClient targetClient = fhirClientService.getHttpClient(targetServerId);

    ValueSet valueSet = sourceClient.<ValueSet>read(ResourceType.valueSet, resourceId).join();
    if (clearSync) {
      targetClient.search(ResourceType.valueSet, new FhirQueryParams(Map.of("url", List.of(valueSet.getUrl())))).join().getEntry().forEach(e -> {
        targetClient.delete(ResourceType.valueSet, ((ValueSet) e.getResource()).getId()).join();
      });
    }
    targetClient.update(valueSet.getId(), valueSet).join();
  }
}
