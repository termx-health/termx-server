package com.kodality.termserver.ts.mapset;

import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.ApiError;
import com.kodality.termserver.PublicationStatus;

import com.kodality.termserver.auth.auth.UserPermissionService;
import com.kodality.termserver.mapset.MapSetEntityVersion;
import com.kodality.termserver.mapset.MapSetVersion;
import com.kodality.termserver.mapset.MapSetVersionQueryParams;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class MapSetVersionService {
  private final MapSetVersionRepository repository;
  private final UserPermissionService userPermissionService;

  @Transactional
  public void save(MapSetVersion version) {
    userPermissionService.checkPermitted(version.getMapSet(), "MapSet", "edit");

    if (!PublicationStatus.draft.equals(version.getStatus()) && version.getId() == null) {
      throw ApiError.TE101.toApiException();
    }
    if (!PublicationStatus.draft.equals(version.getStatus())) {
      repository.saveExpirationDate(version);
      return;
    }
    MapSetVersion lastDraftVersion = repository.query(new MapSetVersionQueryParams()
        .setMapSet(version.getMapSet())
        .setVersion(version.getVersion())
        .setStatus(PublicationStatus.draft)).findFirst().orElse(null);
    if (lastDraftVersion != null && !lastDraftVersion.getId().equals(version.getId())) {
      throw ApiError.TE102.toApiException(Map.of("version", lastDraftVersion.getVersion()));
    }
    version.setCreated(version.getCreated() == null ? OffsetDateTime.now() : version.getCreated());
    repository.save(version);
  }

  public QueryResult<MapSetVersion> query(MapSetVersionQueryParams params) {
    return repository.query(params);
  }

  public Optional<MapSetVersion> load(String mapSet, String versionCode) {
    return Optional.ofNullable(repository.load(mapSet, versionCode));
  }

  @Transactional
  public void activate(String mapSet, String version) {
    userPermissionService.checkPermitted(mapSet, "MapSet", "publish");

    MapSetVersion currentVersion = repository.load(mapSet, version);
    if (currentVersion == null) {
      throw ApiError.TE401.toApiException(Map.of("version", version, "mapSet", mapSet));
    }
    if (PublicationStatus.active.equals(currentVersion.getStatus())) {
      log.warn("Version '{}' of mapSet '{}' is already activated, skipping activation process.", version, mapSet);
      return;
    }

    MapSetVersion overlappingVersion = repository.query(new MapSetVersionQueryParams()
        .setMapSet(mapSet)
        .setStatus(PublicationStatus.active)
        .setReleaseDateLe(currentVersion.getExpirationDate())
        .setExpirationDateGe(currentVersion.getReleaseDate())).findFirst().orElse(null);
    if (overlappingVersion != null) {
      throw ApiError.TE103.toApiException(Map.of("version", overlappingVersion.getVersion()));
    }
    repository.activate(mapSet, version);
  }

  @Transactional
  public void retire(String mapSet, String version) {
    userPermissionService.checkPermitted(mapSet, "MapSet", "publish");

    MapSetVersion currentVersion = repository.load(mapSet, version);
    if (currentVersion == null) {
      throw ApiError.TE401.toApiException(Map.of("version", version, "mapSet", mapSet));
    }
    if (PublicationStatus.retired.equals(currentVersion.getStatus())) {
      log.warn("Version '{}' of mapset '{}' is already retired, skipping retirement process.", version, mapSet);
      return;
    }
    repository.retire(mapSet, version);
  }

  @Transactional
  public void saveEntityVersions(Long mapSetVersionId, List<MapSetEntityVersion> entityVersions) {
    repository.retainEntityVersions(entityVersions, mapSetVersionId);
    repository.upsertEntityVersions(entityVersions, mapSetVersionId);
  }

  @Transactional
  public void linkEntityVersion(String mapSet, String mapSetVersion, Long entityVersionId) {
    userPermissionService.checkPermitted(mapSet, "MapSet", "edit");

    Optional<Long> currentVersionId = load(mapSet, mapSetVersion).map(MapSetVersion::getId);
    if (currentVersionId.isPresent()) {
      repository.linkEntityVersion(currentVersionId.get(), entityVersionId);
    } else {
      throw ApiError.TE401.toApiException(Map.of("version", mapSetVersion, "mapSet", mapSet));
    }
  }

  @Transactional
  public void unlinkEntityVersion(String mapSet, String mapSetVersion, Long entityVersionId) {
    userPermissionService.checkPermitted(mapSet, "MapSet", "edit");

    Optional<Long> currentVersionId = load(mapSet, mapSetVersion).map(MapSetVersion::getId);
    if (currentVersionId.isPresent()) {
      repository.unlinkEntityVersion(currentVersionId.get(), entityVersionId);
    } else {
      throw ApiError.TE401.toApiException(Map.of("version", mapSetVersion, "mapSet", mapSet));
    }
  }

  public MapSetVersion loadLastVersion(String mapSet, String status) {
    return repository.loadLastVersion(mapSet, status);
  }

  @Transactional
  public void cancel(Long versionId, String mapSet) {
    userPermissionService.checkPermitted(mapSet, "MapSet", "publish");
    repository.cancel(versionId);
  }
}
