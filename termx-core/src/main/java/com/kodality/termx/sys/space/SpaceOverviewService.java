package com.kodality.termx.sys.space;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.kodality.termx.sys.server.TerminologyServer;
import com.kodality.termx.sys.server.TerminologyServerService;
import com.kodality.termx.sys.space.overview.SpaceOverview;
import com.kodality.termx.sys.space.overview.SpaceOverview.SpaceOverviewPackage;
import com.kodality.termx.sys.space.overview.SpaceOverviewResponse;
import com.kodality.termx.sys.spacepackage.PackageVersion.PackageResource;
import com.kodality.termx.sys.spacepackage.resource.PackageResourceService;
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
public class SpaceOverviewService {
  private final SpaceService spaceService;
  private final PackageResourceService packageResourceService;
  private final TerminologyServerService terminologyServerService;

  public SpaceOverviewResponse compose(Long spaceId, String packageCode, String version) {
    Space space = spaceService.load(spaceId);
    SpaceOverview overview = new SpaceOverview();
    overview.setCode(space.getCode());
    if (packageCode != null) {
      overview.setPack(new SpaceOverviewPackage().setCode(packageCode).setVersion(version));
    }

    List<PackageResource> resources = packageResourceService.loadAll(spaceId, packageCode, version);
    overview.setCodeSystem(getResourceIds(resources, "code-system"));
    overview.setValueSet(getResourceIds(resources, "value-set"));
    overview.setMapSet(getResourceIds(resources, "map-set"));
    overview.setPage(getResourceIds(resources, "page"));
    overview.setStructureDefinition(getResourceIds(resources, "structure-definition"));
    overview.setSourceOfTruth(groupByTerminologyServer(resources));
    return new SpaceOverviewResponse().setContent(toYaml(overview));
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
