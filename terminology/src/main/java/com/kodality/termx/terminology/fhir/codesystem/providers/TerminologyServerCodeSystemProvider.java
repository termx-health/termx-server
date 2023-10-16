package com.kodality.termx.terminology.fhir.codesystem.providers;

import com.kodality.termx.sys.ResourceType;
import com.kodality.termx.sys.server.resource.TerminologyServerResourceProvider;
import com.kodality.termx.sys.server.resource.TerminologyServerResourceSyncProvider;
import com.kodality.termx.terminology.terminology.FhirServerHttpClientService;
import com.kodality.zmei.fhir.client.FhirClient;
import com.kodality.zmei.fhir.client.FhirClientError;
import com.kodality.zmei.fhir.resource.terminology.CodeSystem;
import jakarta.inject.Singleton;
import java.util.concurrent.CompletionException;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class TerminologyServerCodeSystemProvider implements TerminologyServerResourceProvider, TerminologyServerResourceSyncProvider {
  private final FhirServerHttpClientService fhirClientService;

  @Override
  public String getType() {
    return ResourceType.codeSystem;
  }

  @Override
  public Object getResource(Long serverId, String resourceId) {
    FhirClient client = fhirClientService.getHttpClient(serverId);
    return client.read("CodeSystem", resourceId).join();
  }

  @Override
  public void sync(Long sourceServerId, Long targetServerId, String resourceId) {
    FhirClient sourceClient = fhirClientService.getHttpClient(sourceServerId);
    FhirClient targetClient = fhirClientService.getHttpClient(targetServerId);

    CodeSystem codeSystem = sourceClient.<CodeSystem>read("CodeSystem", resourceId).join();

    try {
      targetClient.<CodeSystem>read("CodeSystem", resourceId).join();
      targetClient.update(resourceId, codeSystem).join();
    } catch (CompletionException e)  {
      if (e.getCause() instanceof FhirClientError && ((FhirClientError) e.getCause()).getResponse().statusCode() == 404) {
        targetClient.create(codeSystem).join();
      } else {
        throw e;
      }
    }
  }
}
