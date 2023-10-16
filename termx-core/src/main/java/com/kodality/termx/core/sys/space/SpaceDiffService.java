package com.kodality.termx.core.sys.space;

import com.kodality.termx.core.ApiError;
import com.kodality.termx.sys.server.TerminologyServer;
import com.kodality.termx.core.sys.server.TerminologyServerResourceService;
import com.kodality.termx.core.sys.server.TerminologyServerService;
import com.kodality.termx.sys.server.resource.TerminologyServerResourceRequest;
import com.kodality.termx.sys.space.diff.SpaceDiff;
import com.kodality.termx.sys.space.diff.SpaceDiff.SpaceDiffItem;
import com.kodality.termx.sys.spacepackage.PackageVersion.PackageResource;
import com.kodality.termx.core.sys.spacepackage.resource.PackageResourceService;
import java.util.List;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class SpaceDiffService {
  private final PackageResourceService packageResourceService;
  private final TerminologyServerService terminologyServerService;
  private final TerminologyServerResourceService terminologyServerResourceService;

  public SpaceDiff findDiff(Long spaceId, String packageCode, String version) {
    TerminologyServer currentServer = terminologyServerService.loadCurrentInstallation();
    if (currentServer == null) {
      throw ApiError.TC105.toApiException();
    }
    List<PackageResource> resources = packageResourceService.loadAll(spaceId, packageCode, version);
    List<SpaceDiffItem> items = resources.stream().map(resource -> {
      SpaceDiffItem item = new SpaceDiffItem();
      item.setResourceId(resource.getResourceId());
      item.setResourceType(resource.getResourceType());
      item.setResourceServer(resource.getTerminologyServer());
      item.setUpToDate(isUpToDate(resource, currentServer));
      return item;
    }).toList();
    return new SpaceDiff().setItems(items);
  }

  private boolean isUpToDate(PackageResource resource, TerminologyServer currentServer) {
    if (resource.getTerminologyServer() == null || resource.getTerminologyServer().equals(currentServer.getCode())) {
      return true;
    }
    TerminologyServerResourceRequest request = new TerminologyServerResourceRequest()
        .setResourceId(resource.getResourceId())
        .setResourceType(resource.getResourceType())
        .setServerCode(currentServer.getCode());
    String current = terminologyServerResourceService.getResource(request).getResource();

    request.setServerCode(resource.getTerminologyServer());
    String comparable = terminologyServerResourceService.getResource(request).getResource();

    return current != null && current.equals(comparable);
  }
}
