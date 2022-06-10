package com.kodality.termserver.ts.mapset.entity;

import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.ApiError;
import com.kodality.termserver.PublicationStatus;
import com.kodality.termserver.mapset.MapSetEntityVersion;
import com.kodality.termserver.mapset.MapSetEntityVersionQueryParams;
import java.time.OffsetDateTime;
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

  @Transactional
  public MapSetEntityVersion save(MapSetEntityVersion version, Long mapSetEntityId) {
    version.setCreated(version.getCreated() == null ? OffsetDateTime.now() : version.getCreated());
    repository.save(version, mapSetEntityId);
    return version;
  }

  public QueryResult<MapSetEntityVersion> query(MapSetEntityVersionQueryParams params) {
    return repository.query(params);
  }


  @Transactional
  public void activate(Long versionId) {
    MapSetEntityVersion currentVersion = repository.getVersion(versionId);
    if (currentVersion == null) {
      throw ApiError.TE105.toApiException(Map.of("version", versionId));
    }
    if (PublicationStatus.active.equals(currentVersion.getStatus())) {
      log.warn("Version '{}' is already activated, skipping activation process.", versionId);
      return;
    }
    repository.activate(versionId);
  }

  @Transactional
  public void retire(Long versionId) {
    MapSetEntityVersion currentVersion = repository.getVersion(versionId);
    if (currentVersion == null) {
      throw ApiError.TE105.toApiException(Map.of("version", versionId));
    }
    if (PublicationStatus.retired.equals(currentVersion.getStatus())) {
      log.warn("Version '{}' is already retired, skipping retirement process.", versionId);
      return;
    }
    repository.retire(versionId);
  }
}
