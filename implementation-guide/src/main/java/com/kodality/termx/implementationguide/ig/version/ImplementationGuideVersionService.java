package com.kodality.termx.implementationguide.ig.version;

import com.kodality.commons.model.QueryResult;
import com.kodality.termx.implementationguide.ApiError;
import com.kodality.termx.implementationguide.ig.version.group.ImplementationGuideGroup;
import com.kodality.termx.implementationguide.ig.version.group.ImplementationGuideGroupService;
import com.kodality.termx.implementationguide.ig.version.page.ImplementationGuidePage;
import com.kodality.termx.implementationguide.ig.version.page.ImplementationGuidePageService;
import com.kodality.termx.implementationguide.ig.version.resource.ImplementationGuideResource;
import com.kodality.termx.implementationguide.ig.version.resource.ImplementationGuideResourceService;
import com.kodality.termx.ts.PublicationStatus;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class ImplementationGuideVersionService {
  private final ImplementationGuideVersionRepository repository;
  private final ImplementationGuideGroupService implementationGuideGroupService;
  private final ImplementationGuideResourceService implementationGuideResourceService;
  private final ImplementationGuidePageService implementationGuidePageService;

  public QueryResult<ImplementationGuideVersion> query(ImplementationGuideVersionQueryParams params) {
    return repository.query(params);
  }

  @Transactional
  public void save(ImplementationGuideVersion version) {
    Optional<ImplementationGuideVersion> existingVersion = load(version.getImplementationGuide(), version.getVersion());
    if (version.getId() == null) {
      version.setId(existingVersion.map(ImplementationGuideVersion::getId).orElse(null));
    }
    if (!PublicationStatus.draft.equals(version.getStatus()) && version.getId() == null) {
      throw ApiError.IG101.toApiException();
    }
    if (existingVersion.isPresent() && !existingVersion.get().getId().equals(version.getId())) {
      throw ApiError.IG102.toApiException(Map.of("version", existingVersion.get().getVersion()));
    }
    repository.save(version);
  }

  public Optional<ImplementationGuideVersion> load(String ig, String version) {
    return Optional.ofNullable(repository.load(ig, version));
  }


  @Transactional
  public void changeStatus(String ig, String version, String status) {
    ImplementationGuideVersion currentVersion = load(ig, version).orElseThrow(() -> ApiError.IG104.toApiException(Map.of("version", version, "ig", ig)));
    if (status.equals(currentVersion.getStatus())) {
      log.warn("Version '{}' of implementation guide '{}' is already activated, skipping activation process.", version, ig);
      return;
    }
    repository.changeStatus(ig, version, status);
  }

  @Transactional
  public void saveGroups(String ig, String version, List<ImplementationGuideGroup> groups) {
    ImplementationGuideVersion igVersion = load(ig, version).orElseThrow(() -> ApiError.IG104.toApiException(Map.of("version", version, "ig", ig)));
    implementationGuideGroupService.save(ig, igVersion.getId(), groups);
  }

  public List<ImplementationGuideResource> loadResources(String ig, String version) {
    ImplementationGuideVersion igVersion = load(ig, version).orElseThrow(() -> ApiError.IG104.toApiException(Map.of("version", version, "ig", ig)));
    return implementationGuideResourceService.loadAll(igVersion.getId());
  }


  @Transactional
  public void saveResources(String ig, String version, List<ImplementationGuideResource> resources) {
    ImplementationGuideVersion igVersion = load(ig, version).orElseThrow(() -> ApiError.IG104.toApiException(Map.of("version", version, "ig", ig)));
    implementationGuideResourceService.save(ig, igVersion.getId(), resources);
  }

  public List<ImplementationGuidePage> loadPages(String ig, String version) {
    ImplementationGuideVersion igVersion = load(ig, version).orElseThrow(() -> ApiError.IG104.toApiException(Map.of("version", version, "ig", ig)));
    return implementationGuidePageService.loadAll(igVersion.getId());
  }


  @Transactional
  public void savePages(String ig, String version, List<ImplementationGuidePage> pages) {
    ImplementationGuideVersion igVersion = load(ig, version).orElseThrow(() -> ApiError.IG104.toApiException(Map.of("version", version, "ig", ig)));
    implementationGuidePageService.save(ig, igVersion.getId(), pages);
  }

}
