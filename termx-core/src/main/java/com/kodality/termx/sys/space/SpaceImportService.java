package com.kodality.termx.sys.space;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.kodality.commons.model.LocalizedName;
import com.kodality.commons.util.MapUtil;
import com.kodality.termx.ApiError;
import com.kodality.termx.sys.ResourceType;
import com.kodality.termx.sys.server.TerminologyServer;
import com.kodality.termx.sys.server.TerminologyServerQueryParams;
import com.kodality.termx.sys.server.TerminologyServerService;
import com.kodality.termx.sys.space.overview.SpaceOverview;
import com.kodality.termx.sys.space.resource.SpaceResourceProvider;
import com.kodality.termx.sys.spacepackage.Package;
import com.kodality.termx.sys.spacepackage.PackageService;
import com.kodality.termx.sys.spacepackage.PackageStatus;
import com.kodality.termx.sys.spacepackage.PackageVersion;
import com.kodality.termx.sys.spacepackage.PackageVersion.PackageResource;
import com.kodality.termx.sys.spacepackage.version.PackageVersionService;
import com.kodality.termx.ts.Language;
import io.micronaut.core.util.CollectionUtils;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class SpaceImportService {
  private final SpaceService spaceService;
  private final PackageService packageService;
  private final PackageVersionService packageVersionService;
  private final TerminologyServerService terminologyServerService;

  private final List<SpaceResourceProvider> resourceProviders;

  @Transactional
  public void importSpace(String yaml) {
    SpaceOverview overview = fromYaml(yaml);

    if (overview == null || overview.getCode() == null) {
      throw ApiError.TC103.toApiException();
    }
    if (overview.getPack() == null ||
        overview.getPack().getCode() == null ||
        overview.getPack().getVersion() == null
    ) {
      throw ApiError.TC104.toApiException();
    }

    List<TerminologyServer> terminologyServers = loadServers(overview.getSourceOfTruth());
    List<PackageResource> resources = toPackageResources(overview.getSourceOfTruth());

    resources = importExternalResources(resources, terminologyServers);
    resources.addAll(findLocalResources(overview, resources));

    Space space = prepareSpace(overview.getCode(), terminologyServers);
    Package pack = preparePackage(space.getId(), overview.getPack().getCode());

    savePackageVersion(pack.getId(), overview.getPack().getVersion(), resources);
  }

  private Space prepareSpace(String code, List<TerminologyServer> terminologyServers) {
    Space space = spaceService.load(code);
    if (space == null) {
      space = new Space();
      space.setActive(true);
      space.setCode(code);
      space.setNames(new LocalizedName(Map.of(Language.en, code)));
    }
    space.setTerminologyServers(terminologyServers.stream().map(TerminologyServer::getCode).toList());
    return spaceService.save(space);
  }

  private Package preparePackage(Long spaceId, String code) {
    Package pack = packageService.load(spaceId, code);
    if (pack == null) {
      pack = new Package();
      pack.setCode(code);
      pack.setStatus(PackageStatus.active);
      packageService.save(pack, spaceId);
    }
    return pack;
  }

  private void savePackageVersion(Long packageId, String ver, List<PackageResource> resources) {
    PackageVersion version = packageVersionService.load(packageId, ver);
    if (version == null) {
      version = new PackageVersion();
      version.setVersion(ver);
    }
    version.setResources(resources);
    packageVersionService.save(packageId, version);
  }


  private List<PackageResource> importExternalResources(List<PackageResource> extResources, List<TerminologyServer> extServers) {
    Map<String, Integer> importPriority = Map.of(
        ResourceType.codeSystem, 1,
        ResourceType.valueSet, 2,
        ResourceType.mapSet, 3
    );

    List<String> succeededIds = extResources.stream()
        .collect(Collectors.groupingBy(PackageResource::getResourceType))
        .entrySet()
        .stream().sorted(Comparator.comparing(e -> importPriority.get(e.getKey())))
        .flatMap(e -> {
          SpaceResourceProvider provider = resourceProviders.stream().filter(p -> p.getType().equals(e.getKey())).findFirst().orElseThrow();
          return provider.importResource(e.getValue(), extServers).stream();
        })
        .toList();

    return extResources.stream()
        .filter(resource -> succeededIds.stream().anyMatch(id -> id.equals(resource.getResourceId())))
        .toList();
  }

  private List<PackageResource> findLocalResources(SpaceOverview overview, List<PackageResource> externalResources) {
    Map<String, List<String>> types = MapUtil.toMap(
        ResourceType.codeSystem, overview.getCodeSystem(),
        ResourceType.valueSet, overview.getValueSet(),
        ResourceType.mapSet, overview.getMapSet()
    );

    List<PackageResource> resources = types.entrySet().stream().flatMap(e -> {
      return resourceProviders
          .stream()
          .filter(p -> p.getType().equals(e.getKey()))
          .findFirst()
          .orElseThrow()
          .queryExistingResources(e.getValue())
          .stream()
          .map(id -> new PackageResource().setResourceId(id).setResourceType(e.getKey()));
    }).toList();

    return resources.stream()
        .filter(
            r -> externalResources.stream().noneMatch(er -> er.getResourceType().equals(r.getResourceType()) && er.getResourceId().equals(r.getResourceId())))
        .toList();
  }


  private List<TerminologyServer> loadServers(Map<String, Map<String, List<String>>> sourceOfTruth) {
    if (CollectionUtils.isEmpty(sourceOfTruth)) {
      return List.of();
    }

    List<String> serverCodes = sourceOfTruth.keySet().stream().toList();
    TerminologyServerQueryParams params = new TerminologyServerQueryParams();
    params.setCodes(String.join(",", serverCodes));
    params.limit(serverCodes.size());
    List<TerminologyServer> servers = terminologyServerService.query(params).getData();

    if (servers.size() < serverCodes.size()) {
      List<String> existing = servers.stream().map(TerminologyServer::getCode).toList();
      throw new RuntimeException("Servers missing: %s".formatted(ListUtils.subtract(serverCodes, existing)));
    }
    return servers;
  }

  private List<PackageResource> toPackageResources(Map<String, Map<String, List<String>>> sourceOfTruth) {
    if (CollectionUtils.isEmpty(sourceOfTruth)) {
      return List.of();
    }

    return sourceOfTruth.keySet().stream()
        .flatMap(serverName -> sourceOfTruth.get(serverName).keySet().stream()
            .flatMap(type -> sourceOfTruth.get(serverName).get(type).stream()
                .map(id -> {
                  PackageResource resource = new PackageResource();
                  resource.setTerminologyServer(serverName);
                  resource.setResourceType(type);
                  resource.setResourceId(id);
                  return resource;
                }))).toList();
  }

  private SpaceOverview fromYaml(String yaml) {
    try {
      ObjectMapper yamlReader = new ObjectMapper(new YAMLFactory());
      return yamlReader.readValue(yaml, SpaceOverview.class);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

}
