package com.kodality.termserver.sys.server;

import com.kodality.commons.util.JsonUtil;
import com.kodality.termserver.ApiError;
import com.kodality.termserver.sys.server.resource.TerminologyServerResourceProvider;
import com.kodality.termserver.sys.server.resource.TerminologyServerResourceRequest;
import com.kodality.termserver.sys.server.resource.TerminologyServerResourceResponse;
import java.util.List;
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
        .orElseThrow(ApiError.TC102::toApiException);

    Object resource = provider.getResource(server.getRootUrl(), request.getResourceId());
    return new TerminologyServerResourceResponse().setResource(JsonUtil.toPrettyJson(resource));
  }
}
