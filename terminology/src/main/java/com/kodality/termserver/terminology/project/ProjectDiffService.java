package com.kodality.termserver.terminology.project;

import com.kodality.termserver.ApiError;
import com.kodality.termserver.ts.project.diff.ProjectDiff;
import com.kodality.termserver.ts.project.diff.ProjectDiff.ProjectDiffItem;
import com.kodality.termserver.ts.project.diff.ProjectDiffRequest;
import com.kodality.termserver.ts.project.projectpackage.PackageVersion.PackageResource;
import com.kodality.termserver.terminology.project.projectpackage.resource.PackageResourceService;
import com.kodality.termserver.ts.project.server.TerminologyServer;
import com.kodality.termserver.ts.project.server.TerminologyServerResourceRequest;
import com.kodality.termserver.terminology.project.server.TerminologyServerResourceService;
import com.kodality.termserver.terminology.project.server.TerminologyServerService;
import java.util.List;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class ProjectDiffService {
  private final PackageResourceService packageResourceService;
  private final TerminologyServerService terminologyServerService;
  private final TerminologyServerResourceService terminologyServerResourceService;

  public ProjectDiff findDiff(ProjectDiffRequest request) {
    TerminologyServer currentServer = terminologyServerService.loadCurrentInstallation();
    if (currentServer == null) {
      throw ApiError.TE905.toApiException();
    }

    if (request.getProjectCode() == null && request.getPackageCode() == null && request.getVersion() == null) {
      return new ProjectDiff();
    }

    List<PackageResource> resources = packageResourceService.loadAll(request.getProjectCode(), request.getPackageCode(), request.getVersion());
    List<ProjectDiffItem> items = resources.stream().map(resource -> {
      ProjectDiffItem item = new ProjectDiffItem();
      item.setResourceId(resource.getResourceId());
      item.setResourceType(resource.getResourceType());
      item.setResourceServer(resource.getTerminologyServer());
      item.setUpToDate(isUpToDate(resource, currentServer));
      return item;
    }).toList();
    return new ProjectDiff().setItems(items);
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
