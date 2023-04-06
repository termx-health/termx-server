package com.kodality.termserver.terminology.mapset.entity;

import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.auth.UserPermissionService;
import com.kodality.termserver.exception.ApiError;
import com.kodality.termserver.ts.PublicationStatus;
import com.kodality.termserver.ts.mapset.MapSetEntityVersion;
import com.kodality.termserver.ts.mapset.MapSetEntityVersionQueryParams;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class MapSetEntityVersionService {
  private final MapSetEntityVersionRepository repository;
  private final UserPermissionService userPermissionService;

  public MapSetEntityVersion load(Long id) {
    return repository.load(id);
  }

  @Transactional
  public MapSetEntityVersion save(MapSetEntityVersion version, Long mapSetEntityId) {
    userPermissionService.checkPermitted(version.getMapSet(), "MapSet", "edit");

    version.setCreated(version.getCreated() == null ? OffsetDateTime.now() : version.getCreated());
    repository.save(version, mapSetEntityId);
    return version;
  }

  @Transactional
  public void save(List<MapSetEntityVersion> versions, Long mapSetEntityId) {
    repository.retainVersions(versions, mapSetEntityId);
    if (versions != null) {
      versions.forEach(version -> save(version, mapSetEntityId));
    }
  }

  public QueryResult<MapSetEntityVersion> query(MapSetEntityVersionQueryParams params) {
    return repository.query(params);
  }

  @Transactional
  public void activate(Long versionId) {
    MapSetEntityVersion currentVersion = repository.load(versionId);
    if (currentVersion == null) {
      throw ApiError.TE105.toApiException(Map.of("version", versionId));
    }
    userPermissionService.checkPermitted(currentVersion.getMapSet(), "MapSet", "edit");
    if (PublicationStatus.active.equals(currentVersion.getStatus())) {
      log.warn("Version '{}' is already activated, skipping activation process.", versionId);
      return;
    }
    repository.activate(versionId);
  }

  @Transactional
  public void retire(Long versionId) {
    MapSetEntityVersion currentVersion = repository.load(versionId);
    if (currentVersion == null) {
      throw ApiError.TE105.toApiException(Map.of("version", versionId));
    }
    userPermissionService.checkPermitted(currentVersion.getMapSet(), "MapSet", "edit");
    if (PublicationStatus.retired.equals(currentVersion.getStatus())) {
      log.warn("Version '{}' is already retired, skipping retirement process.", versionId);
      return;
    }
    repository.retire(versionId);
  }

  @Transactional
  public void cancel(Long versionId, String mapSet) {
    userPermissionService.checkPermitted(mapSet, "MapSet", "edit");
    repository.cancel(versionId);
  }
}
