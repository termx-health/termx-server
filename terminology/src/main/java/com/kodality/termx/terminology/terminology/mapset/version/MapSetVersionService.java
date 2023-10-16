package com.kodality.termx.terminology.terminology.mapset.version;

import com.kodality.commons.model.QueryResult;
import com.kodality.termx.terminology.ApiError;
import com.kodality.termx.terminology.terminology.codesystem.CodeSystemService;
import com.kodality.termx.terminology.terminology.valueset.ValueSetService;
import com.kodality.termx.ts.PublicationStatus;
import com.kodality.termx.ts.codesystem.CodeSystem;
import com.kodality.termx.ts.mapset.MapSetVersion;
import com.kodality.termx.ts.mapset.MapSetVersionQueryParams;
import com.kodality.termx.ts.mapset.MapSetVersionReference;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class MapSetVersionService {
  private final MapSetVersionRepository repository;
  private final ValueSetService valueSetService;
  private final CodeSystemService codeSystemService;

  @Transactional
  public void save(MapSetVersion version) {
    if (version.getId() == null) {
      version.setId(load(version.getMapSet(), version.getVersion()).map(MapSetVersionReference::getId).orElse(null));
    }
    if (!PublicationStatus.draft.equals(version.getStatus()) && version.getId() == null) {
      throw ApiError.TE101.toApiException();
    }
    if (!PublicationStatus.draft.equals(version.getStatus())) {
      repository.saveExpirationDate(version);
      return;
    }
    MapSetVersion existingVersion = repository.query(new MapSetVersionQueryParams()
        .setMapSet(version.getMapSet())
        .setVersion(version.getVersion())).findFirst().orElse(null);
    if (existingVersion != null && !existingVersion.getId().equals(version.getId())) {
      throw ApiError.TE102.toApiException(Map.of("version", existingVersion.getVersion()));
    }
    prepare(version);
    repository.save(version);
  }

  private void prepare(MapSetVersion version) {
    version.setCreated(version.getCreated() == null ? OffsetDateTime.now() : version.getCreated());
    if (version.getScope().getSourceValueSet() != null && version.getScope().getSourceValueSet().getId() != null) {
      version.getScope().getSourceValueSet().setUri(valueSetService.load(version.getScope().getSourceValueSet().getId()).getUri());
    }
    if (version.getScope().getTargetValueSet() != null && version.getScope().getTargetValueSet().getId() != null) {
      version.getScope().getTargetValueSet().setUri(valueSetService.load(version.getScope().getTargetValueSet().getId()).getUri());
    }
    if (CollectionUtils.isNotEmpty(version.getScope().getSourceCodeSystems())) {
      version.getScope().getSourceCodeSystems().stream().filter(cs -> cs.getId() != null)
          .forEach(cs -> cs.setUri(codeSystemService.load(cs.getId()).map(CodeSystem::getUri).orElse(null)));
    }
    if (CollectionUtils.isNotEmpty(version.getScope().getTargetCodeSystems())) {
      version.getScope().getTargetCodeSystems().stream().filter(cs -> cs.getId() != null)
          .forEach(cs -> cs.setUri(codeSystemService.load(cs.getId()).map(CodeSystem::getUri).orElse(null)));
    }
  }

  public QueryResult<MapSetVersion> query(MapSetVersionQueryParams params) {
    return repository.query(params);
  }

  public Optional<MapSetVersion> load(String mapSet, String versionCode) {
    return Optional.ofNullable(repository.load(mapSet, versionCode));
  }

  public MapSetVersion load(Long id) {
    return repository.load(id);
  }

  @Transactional
  public void activate(String mapSet, String version) {
    MapSetVersion currentVersion = repository.load(mapSet, version);
    if (currentVersion == null) {
      throw ApiError.TE401.toApiException(Map.of("version", version, "mapSet", mapSet));
    }
    if (PublicationStatus.active.equals(currentVersion.getStatus())) {
      log.warn("Version '{}' of mapSet '{}' is already activated, skipping activation process.", version, mapSet);
      return;
    }
    repository.activate(mapSet, version);
  }

  @Transactional
  public void retire(String mapSet, String version) {
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
  public void saveAsDraft(String mapSet, String version) {
    MapSetVersion currentVersion = repository.load(mapSet, version);
    if (currentVersion == null) {
      throw ApiError.TE401.toApiException(Map.of("version", version, "mapSet", mapSet));
    }
    if (PublicationStatus.draft.equals(currentVersion.getStatus())) {
      log.warn("Version '{}' of mapset '{}' is already draft, skipping retirement process.", version, mapSet);
      return;
    }
    repository.saveAsDraft(mapSet, version);
  }

  public MapSetVersion loadLastVersion(String mapSet) {
    return repository.loadLastVersion(mapSet);
  }

  @Transactional
  public void cancel(Long versionId) {
    repository.cancel(versionId);
  }
}
