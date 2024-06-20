package com.kodality.termx.core.sys.resource;

import com.kodality.commons.util.JsonUtil;
import com.kodality.termx.core.sys.server.TerminologyServerResourceService;
import com.kodality.termx.sys.ResourceReference;
import com.kodality.termx.sys.server.resource.TerminologyServerResourceRequest;
import java.util.Map;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public abstract class ResourceDiffService {
  private final TerminologyServerResourceService terminologyServerResourceService;

  public boolean isUpToDate(ResourceReference resource, String currentServer, String externalServer) {
    if (externalServer == null || currentServer == null || externalServer.equals(currentServer)) {
      return true;
    }
    TerminologyServerResourceRequest request = new TerminologyServerResourceRequest()
        .setResourceId(resource.getResourceId())
        .setResourceVersion(resource.getResourceVersion())
        .setResourceType(resource.getResourceType())
        .setServerCode(currentServer);
    Map<String, Object> current = JsonUtil.fromJson(terminologyServerResourceService.getResource(request).getResource(), JsonUtil.getMapType(Object.class));
    if (current == null) {
      return false;
    }

    request.setResourceId((String) current.get("id"));
    request.setResourceVersion(null);
    request.setServerCode(externalServer);
    Map<String, Object> comparable = JsonUtil.fromJson(terminologyServerResourceService.getResource(request).getResource(), JsonUtil.getMapType(Object.class));
    if (comparable == null) {
      return false;
    }

    current.remove("meta");
    comparable.remove("meta");
    current.remove("text");
    comparable.remove("text");
    return JsonUtil.toJson(current).equals(JsonUtil.toJson(comparable));
  }
}
