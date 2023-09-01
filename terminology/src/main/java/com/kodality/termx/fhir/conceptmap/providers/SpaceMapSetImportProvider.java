package com.kodality.termx.fhir.conceptmap.providers;

import com.kodality.termx.fhir.conceptmap.ConceptMapFhirImportService;
import com.kodality.termx.sys.ResourceType;
import com.kodality.termx.sys.server.TerminologyServer;
import com.kodality.termx.sys.space.resource.SpaceResourceProvider;
import com.kodality.termx.sys.spacepackage.PackageVersion.PackageResource;
import com.kodality.termx.terminology.mapset.MapSetService;
import com.kodality.termx.ts.mapset.MapSet;
import com.kodality.termx.ts.mapset.MapSetQueryParams;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class SpaceMapSetImportProvider implements SpaceResourceProvider {
  private final MapSetService mapSetService;
  private final ConceptMapFhirImportService msFhirImportService;

  @Override
  public String getType() {
    return ResourceType.mapSet;
  }

  @Override
  public List<String> importResource(List<PackageResource> resources, List<TerminologyServer> extServers) {
    List<String> ids = resources.stream().map(PackageResource::getResourceId).toList();
    List<String> existingIds = queryExistingResources(ids);

    List<ImportUrl> importUrls = getImportUrls(resources, existingIds, extServers);
    List<String> importedIds = importCodeSystems(importUrls);

    return ListUtils.union(existingIds, importedIds);
  }

  @Override
  public List<String> queryExistingResources(List<String> ids) {
    MapSetQueryParams params = new MapSetQueryParams();
    params.setIds(String.join(",", ids));
    params.setLimit(ids.size());
    return mapSetService.query(params).getData().stream().map(MapSet::getId).toList();
  }

  private List<String> importCodeSystems(List<ImportUrl> urls) {
    return urls.stream().map(url -> {
      try {
        msFhirImportService.importMapSetFromUrl(url.url, url.resourceId);
        return url.resourceId;
      } catch (Exception e) {
        log.error(e.getMessage());
        return null;
      }
    }).filter(Objects::nonNull).toList();
  }

  private List<ImportUrl> getImportUrls(List<PackageResource> resources, List<String> existingIds, List<TerminologyServer> servers) {
    return resources.stream()
        .filter(r -> !existingIds.contains(r.getResourceId()))
        .map(r -> servers.stream()
            .filter(s -> s.getCode().equals(r.getTerminologyServer()))
            .findFirst()
            .map(s -> new ImportUrl(r.getResourceId(), "%s/fhir/MapSet/%s".formatted(StringUtils.stripEnd(s.getRootUrl(), "/"), r.getResourceId())))
            .orElse(null))
        .filter(Objects::nonNull)
        .toList();
  }

  private record ImportUrl(String resourceId, String url) {}
}
