package com.kodality.termx.terminology.fhir.codesystem.providers;

import com.kodality.termx.terminology.fhir.codesystem.CodeSystemFhirImportService;
import com.kodality.termx.sys.ResourceType;
import com.kodality.termx.sys.server.TerminologyServer;
import com.kodality.termx.sys.space.resource.SpaceResourceProvider;
import com.kodality.termx.sys.spacepackage.PackageVersion.PackageResource;
import com.kodality.termx.terminology.terminology.codesystem.CodeSystemService;
import com.kodality.termx.ts.codesystem.CodeSystem;
import com.kodality.termx.ts.codesystem.CodeSystemQueryParams;
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
public class SpaceCodeSystemProvider implements SpaceResourceProvider {
  private final CodeSystemService codeSystemService;
  private final CodeSystemFhirImportService csFhirImportService;

  @Override
  public String getType() {
    return ResourceType.codeSystem;
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
    CodeSystemQueryParams params = new CodeSystemQueryParams();
    params.setIds(String.join(",", ids));
    params.setLimit(ids.size());
    return codeSystemService.query(params).getData().stream().map(CodeSystem::getId).toList();
  }

  private List<String> importCodeSystems(List<ImportUrl> urls) {
    return urls.stream().map(url -> {
      try {
        csFhirImportService.importCodeSystemFromUrl(url.url, url.resourceId);
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
            .map(s -> new ImportUrl(r.getResourceId(), "%s/fhir/CodeSystem/%s".formatted(StringUtils.stripEnd(s.getRootUrl(), "/"), r.getResourceId())))
            .orElse(null))
        .filter(Objects::nonNull)
        .toList();
  }

  private record ImportUrl(String resourceId, String url) {}
}
