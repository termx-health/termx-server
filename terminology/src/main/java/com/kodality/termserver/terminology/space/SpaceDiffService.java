package com.kodality.termserver.terminology.space;

import com.kodality.termserver.exception.ApiError;
import com.kodality.termserver.ts.space.diff.SpaceDiff;
import com.kodality.termserver.ts.space.diff.SpaceDiff.SpaceDiffItem;
import com.kodality.termserver.ts.space.diff.SpaceDiffRequest;
import com.kodality.termserver.ts.space.spacepackage.PackageVersion.PackageResource;
import com.kodality.termserver.terminology.space.spacepackage.resource.PackageResourceService;
import com.kodality.termserver.ts.space.server.TerminologyServer;
import com.kodality.termserver.ts.space.server.TerminologyServerResourceRequest;
import com.kodality.termserver.terminology.space.server.TerminologyServerResourceService;
import com.kodality.termserver.terminology.space.server.TerminologyServerService;
import java.util.List;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class SpaceDiffService {
  private final PackageResourceService packageResourceService;
  private final TerminologyServerService terminologyServerService;
  private final TerminologyServerResourceService terminologyServerResourceService;

  public SpaceDiff findDiff(SpaceDiffRequest request) {
    TerminologyServer currentServer = terminologyServerService.loadCurrentInstallation();
    if (currentServer == null) {
      throw ApiError.TE905.toApiException();
    }

    if (request.getSpaceCode() == null && request.getPackageCode() == null && request.getVersion() == null) {
      return new SpaceDiff();
    }

    List<PackageResource> resources = packageResourceService.loadAll(request.getSpaceCode(), request.getPackageCode(), request.getVersion());
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

    return current.equals(comparable);
  }
}
