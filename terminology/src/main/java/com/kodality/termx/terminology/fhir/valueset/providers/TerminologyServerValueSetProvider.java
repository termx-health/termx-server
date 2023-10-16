package com.kodality.termx.terminology.fhir.valueset.providers;

import com.kodality.termx.sys.ResourceType;
import com.kodality.termx.sys.server.resource.TerminologyServerResourceProvider;
import com.kodality.termx.sys.server.resource.TerminologyServerResourceSyncProvider;
import com.kodality.termx.terminology.terminology.FhirServerHttpClientService;
import com.kodality.zmei.fhir.client.FhirClient;
import com.kodality.zmei.fhir.client.FhirClientError;
import com.kodality.zmei.fhir.resource.terminology.ValueSet;
import jakarta.inject.Singleton;
import java.util.concurrent.CompletionException;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class TerminologyServerValueSetProvider implements TerminologyServerResourceProvider, TerminologyServerResourceSyncProvider {
  private final FhirServerHttpClientService fhirClientService;

  @Override
  public String getType() {
    return ResourceType.valueSet;
  }

  @Override
  public Object getResource(Long serverId, String resourceId) {
    FhirClient client = fhirClientService.getHttpClient(serverId);
    return client.read("ValueSet", resourceId).join();
  }

  @Override
  public void sync(Long sourceServerId, Long targetServerId, String resourceId) {
    FhirClient sourceClient = fhirClientService.getHttpClient(sourceServerId);
    FhirClient targetClient = fhirClientService.getHttpClient(targetServerId);

    ValueSet valueSet = sourceClient.<ValueSet>read("ValueSet", resourceId).join();

    try {
      targetClient.<ValueSet>read("ValueSet", resourceId).join();
      targetClient.update(resourceId, valueSet).join();
    } catch (CompletionException e)  {
      if (e.getCause() instanceof FhirClientError && ((FhirClientError) e.getCause()).getResponse().statusCode() == 404) {
        targetClient.create(valueSet).join();
      }
    }
  }
}
