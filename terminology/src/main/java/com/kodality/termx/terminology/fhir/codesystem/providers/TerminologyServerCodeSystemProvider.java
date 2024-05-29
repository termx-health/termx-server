package com.kodality.termx.terminology.fhir.codesystem.providers;

import com.kodality.termx.sys.server.resource.TerminologyServerResourceProvider;
import com.kodality.termx.sys.server.resource.TerminologyServerResourceSyncProvider;
import com.kodality.termx.terminology.terminology.FhirServerHttpClientService;
import com.kodality.termx.terminology.terminology.FhirServerHttpClientService.FhirServerHttpClient;
import com.kodality.zmei.fhir.resource.ResourceType;
import com.kodality.zmei.fhir.resource.terminology.CodeSystem;
import com.kodality.zmei.fhir.search.FhirQueryParams;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class TerminologyServerCodeSystemProvider implements TerminologyServerResourceProvider, TerminologyServerResourceSyncProvider {
  private final FhirServerHttpClientService fhirClientService;
  private final static Set<String> types = Set.of("code-system", "CodeSystem");

  @Override
  public boolean checkType(String type) {
    return types.contains(type);
  }

  @Override
  public Object getResource(Long serverId, String resourceId) {
    FhirServerHttpClient client = fhirClientService.getHttpClient(serverId);
    return client.read(ResourceType.codeSystem, resourceId).join();
  }

  @Override
  public void sync(Long sourceServerId, Long targetServerId, String resourceId, boolean clearSync) {
    FhirServerHttpClient sourceClient = fhirClientService.getHttpClient(sourceServerId);
    FhirServerHttpClient targetClient = fhirClientService.getHttpClient(targetServerId);

    CodeSystem codeSystem = sourceClient.<CodeSystem>read(ResourceType.codeSystem, resourceId).join();
    if (clearSync) {
      if (codeSystem.getUrl().equals("http://snomed.info/sct") || codeSystem.getUrl().startsWith("http://snomed.info/sct")) {
        throw new RuntimeException();
      }
      targetClient.search(ResourceType.codeSystem, new FhirQueryParams(Map.of("url", List.of(codeSystem.getUrl())))).join().getEntry().forEach(e -> {
        targetClient.delete(ResourceType.codeSystem, ((CodeSystem) e.getResource()).getId()).join();
      });
    }
    targetClient.update(codeSystem.getId(), codeSystem).join();
  }
}
