package com.kodality.termserver.ts.mapset;

import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.ApiError;
import com.kodality.termserver.PublicationStatus;

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

  @Transactional
  public void save(MapSetVersion version) {
    if (!PublicationStatus.draft.equals(version.getStatus())) {
      throw ApiError.TE101.toApiException();
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

  public Optional<MapSetVersion> getVersion(String mapSet, String versionCode) {
    return Optional.ofNullable(repository.getVersion(mapSet, versionCode));
  }

  public List<MapSetVersion> getVersions(String mapSet) {
    return repository.getVersions(mapSet);
  }

  @Transactional
  public void activate(String mapSet, String version) {
    MapSetVersion currentVersion = repository.getVersion(mapSet, version);
    if (currentVersion == null) {
      throw ApiError.TE107.toApiException(Map.of("version", version, "mapSet", mapSet));
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
    MapSetVersion currentVersion = repository.getVersion(mapSet, version);
    if (currentVersion == null) {
      throw ApiError.TE107.toApiException(Map.of("version", version, "mapSet", mapSet));
    }
    if (PublicationStatus.retired.equals(currentVersion.getStatus())) {
      log.warn("Version '{}' of mapset '{}' is already retired, skipping retirement process.", version, mapSet);
      return;
    }
    repository.retire(mapSet, version);
  }

  @Transactional
  public void saveEntityVersions(String mapSet, String mapSetVersion, List<MapSetEntityVersion> entityVersions) {
    Optional<Long> versionId = getVersion(mapSet, mapSetVersion).map(MapSetVersion::getId);
    if (versionId.isPresent()) {
      saveEntityVersions(versionId.get(), entityVersions);
    } else {
      throw ApiError.TE107.toApiException(Map.of("version", mapSetVersion, "mapSet", mapSet));
    }
  }

  @Transactional
  public void saveEntityVersions(Long mapSetVersionId, List<MapSetEntityVersion> entityVersions) {
    repository.retainEntityVersions(entityVersions, mapSetVersionId);
    repository.upsertEntityVersions(entityVersions, mapSetVersionId);
  }
}