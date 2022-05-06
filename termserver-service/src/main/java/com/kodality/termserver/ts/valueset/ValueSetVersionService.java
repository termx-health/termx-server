package com.kodality.termserver.ts.valueset;

import com.kodality.termserver.ApiError;
import com.kodality.termserver.PublicationStatus;
import com.kodality.termserver.codesystem.Concept;
import com.kodality.termserver.codesystem.Designation;
import com.kodality.termserver.valueset.ValueSetVersion;
import com.kodality.termserver.valueset.ValueSetVersionQueryParams;
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
public class ValueSetVersionService {
  private final ValueSetVersionRepository repository;

  @Transactional
  public void save(ValueSetVersion version) {
    if (!PublicationStatus.draft.equals(version.getStatus())) {
      throw ApiError.TE101.toApiException();
    }
    ValueSetVersion lastDraftVersion = repository.query(new ValueSetVersionQueryParams()
        .setValueSet(version.getValueSet())
        .setVersion(version.getVersion())
        .setStatus(PublicationStatus.draft)).findFirst().orElse(null);
    if (lastDraftVersion != null && !lastDraftVersion.getId().equals(version.getId())) {
      throw ApiError.TE102.toApiException(Map.of("version", lastDraftVersion.getVersion()));
    }
    version.setCreated(version.getCreated() == null ? OffsetDateTime.now() : version.getCreated());
    repository.save(version);
  }

  public Optional<ValueSetVersion> getVersion(String valueSet, String versionCode) {
    return Optional.ofNullable(repository.getVersion(valueSet, versionCode));
  }

  public List<ValueSetVersion> getVersions(String valueSet) {
    return repository.getVersions(valueSet);
  }

  @Transactional
  public void activate(String valueSet, String version) {
    ValueSetVersion currentVersion = repository.getVersion(valueSet, version);
    if (currentVersion == null) {
      throw ApiError.TE107.toApiException(Map.of("version", version, "valueSet", valueSet));
    }
    if (PublicationStatus.active.equals(currentVersion.getStatus())) {
      log.warn("Version '{}' of valueSet '{}' is already activated, skipping activation process.", version, valueSet);
      return;
    }

    ValueSetVersion overlappingVersion = repository.query(new ValueSetVersionQueryParams()
        .setValueSet(valueSet)
        .setStatus(PublicationStatus.active)
        .setReleaseDateLe(currentVersion.getExpirationDate())
        .setExpirationDateGe(currentVersion.getReleaseDate())).findFirst().orElse(null);
    if (overlappingVersion != null) {
      throw ApiError.TE103.toApiException(Map.of("version", overlappingVersion.getVersion()));
    }
    repository.activate(valueSet, version);
  }

  @Transactional
  public void retire(String valueSet, String version) {
    ValueSetVersion currentVersion = repository.getVersion(valueSet, version);
    if (currentVersion == null) {
      throw ApiError.TE109.toApiException(Map.of("version", version, "valueSet", valueSet));
    }
    if (PublicationStatus.retired.equals(currentVersion.getStatus())) {
      log.warn("Version '{}' of valueSet '{}' is already retired, skipping retirement process.", version, valueSet);
      return;
    }
    repository.retire(valueSet, version);
  }

  @Transactional
  public void saveConcepts(String valueSet, String valueSetVersion, List<Concept> concepts) {
    Optional<Long> versionId = getVersion(valueSet, valueSetVersion).map(ValueSetVersion::getId);
    if (versionId.isPresent()) {
      saveConcepts(versionId.get(), concepts);
    } else {
      throw ApiError.TE109.toApiException(Map.of("version", valueSetVersion, "valueSet", valueSet));
    }
  }

  @Transactional
  public void saveConcepts(Long valueSetVersionId, List<Concept> concepts) {
    repository.retainConcepts(concepts, valueSetVersionId);
    repository.upsertConcepts(concepts, valueSetVersionId);
  }

  @Transactional
  public void saveDesignations(String valueSet, String valueSetVersion, List<Designation> designations) {
    Optional<Long> versionId = getVersion(valueSet, valueSetVersion).map(ValueSetVersion::getId);
    if (versionId.isPresent()) {
      saveDesignations(versionId.get(), designations);
    } else {
      throw ApiError.TE109.toApiException(Map.of("version", valueSetVersion, "valueSet", valueSet));
    }
  }

  @Transactional
  public void saveDesignations(Long valueSetVersionId, List<Designation> designations) {
    repository.retainDesignations(designations, valueSetVersionId);
    repository.upsertDesignations(designations, valueSetVersionId);
  }
}
