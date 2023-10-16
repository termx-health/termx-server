package com.kodality.termx.core.sys.server;

import com.kodality.commons.util.JsonUtil;
import com.kodality.termx.core.ApiError;
import com.kodality.termx.sys.server.TerminologyServer;
import com.kodality.termx.sys.server.resource.TerminologyServerResourceProvider;
import com.kodality.termx.sys.server.resource.TerminologyServerResourceRequest;
import com.kodality.termx.sys.server.resource.TerminologyServerResourceResponse;
import com.kodality.zmei.fhir.client.FhirClientError;
import java.util.List;
import java.util.concurrent.CompletionException;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class TerminologyServerResourceService {
  private final TerminologyServerService serverService;
  private final List<TerminologyServerResourceProvider> resourceProviders;

  public TerminologyServerResourceResponse getResource(TerminologyServerResourceRequest request) {
    TerminologyServer server = serverService.load(request.getServerCode());
    TerminologyServerResourceProvider provider = resourceProviders.stream()
        .filter(p -> p.getType().equals(request.getResourceType()))
        .findFirst()
        .orElseThrow(() -> ApiError.TC102.toApiException());

    TerminologyServerResourceResponse response = new TerminologyServerResourceResponse();
    try {
      Object resource = provider.getResource(server.getId(), request.getResourceId());
      response.setResource(JsonUtil.toPrettyJson(resource));
    } catch (CompletionException e) {
      if (!(e.getCause() instanceof FhirClientError) || 404 != ((FhirClientError) e.getCause()).getResponse().statusCode()) {
        throw e;
      }
    }
    return response;
  }
}
