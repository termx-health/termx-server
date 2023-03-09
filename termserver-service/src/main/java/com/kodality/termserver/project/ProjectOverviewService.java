package com.kodality.termserver.project;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.kodality.termserver.project.overview.ProjectOverview;
import com.kodality.termserver.project.overview.ProjectOverview.ProjectOverviewPackage;
import com.kodality.termserver.project.overview.ProjectOverviewRequest;
import com.kodality.termserver.project.overview.ProjectOverviewResponse;
import com.kodality.termserver.project.projectpackage.PackageVersion.PackageResource;
import com.kodality.termserver.project.projectpackage.resource.PackageResourceService;
import com.kodality.termserver.project.server.TerminologyServer;
import com.kodality.termserver.project.server.TerminologyServerService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;

import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

@Singleton
@RequiredArgsConstructor
public class ProjectOverviewService {
  private final PackageResourceService packageResourceService;
  private final TerminologyServerService terminologyServerService;

  public ProjectOverviewResponse compose(ProjectOverviewRequest request) {
    ProjectOverview overview = new ProjectOverview();
    overview.setCode(request.getProjectCode());
    if (request.getPackageCode() != null) {
      overview.setPack(new ProjectOverviewPackage().setCode(request.getPackageCode()).setVersion(request.getVersion()));
    }

    List<PackageResource> resources = packageResourceService.loadAll(request.getProjectCode(), request.getPackageCode(), request.getVersion());
    overview.setCodeSystem(getResourceIds(resources, "code-system"));
    overview.setValueSet(getResourceIds(resources, "value-set"));
    overview.setMapSet(getResourceIds(resources, "map-set"));
    overview.setPage(getResourceIds(resources, "page"));
    overview.setStructureDefinition(getResourceIds(resources, "structure-definition"));
    overview.setSourceOfTruth(groupByTerminologyServer(resources));
    return new ProjectOverviewResponse().setContent(toYaml(overview));
  }

  private String toYaml(Object obj) {
    try {
      return new YAMLMapper().writeValueAsString(obj);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  private Map<String, Map<String, List<String>>> groupByTerminologyServer(List<PackageResource> resources) {
    TerminologyServer currentServer = terminologyServerService.loadCurrentInstallation();
    if (currentServer != null) {
      resources.forEach(r -> r.setTerminologyServer(r.getTerminologyServer() == null ? currentServer.getCode() : r.getTerminologyServer()));
    }

    Map<String, List<PackageResource>> groupedByServer = resources.stream().filter(r -> r.getTerminologyServer() != null).collect(groupingBy(PackageResource::getTerminologyServer));
    Map<String, Map<String, List<String>>> res = new HashMap<>();
    groupedByServer.keySet().forEach(server ->
        res.put(server, groupedByServer.get(server).stream().collect(
            groupingBy(PackageResource::getResourceType,
                collectingAndThen(toList(), list -> list.stream().map(PackageResource::getResourceId).toList())))));
    return res;
  }

  private List<String> getResourceIds(List<PackageResource> resources, String type) {
    return resources.stream().filter(r -> type.equals(r.getResourceType())).map(PackageResource::getResourceId).sorted().toList();
  }
}
