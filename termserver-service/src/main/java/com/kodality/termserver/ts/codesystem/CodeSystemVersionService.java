package com.kodality.termserver.ts.codesystem;

import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.ApiError;
import com.kodality.termserver.PublicationStatus;
import com.kodality.termserver.codesystem.CodeSystemEntityVersion;
import com.kodality.termserver.codesystem.CodeSystemVersion;
import com.kodality.termserver.codesystem.CodeSystemVersionQueryParams;
import jakarta.inject.Singleton;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class CodeSystemVersionService {
  private final CodeSystemVersionRepository repository;

  @Transactional
  public void save(CodeSystemVersion version) {
    if (!PublicationStatus.draft.equals(version.getStatus())) {
      throw ApiError.TE101.toApiException();
    }
    CodeSystemVersion lastDraftVersion = repository.query(new CodeSystemVersionQueryParams()
        .setCodeSystem(version.getCodeSystem())
        .setVersion(version.getVersion())
        .setStatus(PublicationStatus.draft)).findFirst().orElse(null);
    if (lastDraftVersion != null && !lastDraftVersion.getId().equals(version.getId())) {
      throw ApiError.TE102.toApiException(Map.of("version", lastDraftVersion.getVersion()));
    }
    version.setCreated(version.getCreated() == null ? OffsetDateTime.now() : version.getCreated());
    repository.save(version);
  }

  public Optional<CodeSystemVersion> getVersion(String codeSystem, String versionCode) {
    return Optional.ofNullable(repository.getVersion(codeSystem, versionCode));
  }

  public CodeSystemVersion getVersion(Long id) {
    return repository.getVersion(id);
  }

  public QueryResult<CodeSystemVersion> query(CodeSystemVersionQueryParams params) {
    return repository.query(params);
  }

  @Transactional
  public void activate(String codeSystem, String version) {
    CodeSystemVersion currentVersion = repository.getVersion(codeSystem, version);
    if (currentVersion == null) {
      throw ApiError.TE202.toApiException(Map.of("version", version, "codeSystem", codeSystem));
    }
    if (PublicationStatus.active.equals(currentVersion.getStatus())) {
      log.warn("Version '{}' of codesystem '{}' is already activated, skipping activation process.", version, codeSystem);
      return;
    }

    CodeSystemVersion overlappingVersion = repository.query(new CodeSystemVersionQueryParams()
        .setCodeSystem(codeSystem)
        .setStatus(PublicationStatus.active)
        .setReleaseDateLe(currentVersion.getExpirationDate())
        .setExpirationDateGe(currentVersion.getReleaseDate())).findFirst().orElse(null);
    if (overlappingVersion != null) {
      throw ApiError.TE103.toApiException(Map.of("version", overlappingVersion.getVersion()));
    }
    repository.activate(codeSystem, version);
  }

  @Transactional
  public void retire(String codeSystem, String version) {
    CodeSystemVersion currentVersion = repository.getVersion(codeSystem, version);
    if (currentVersion == null) {
      throw ApiError.TE202.toApiException(Map.of("version", version, "codeSystem", codeSystem));
    }
    if (PublicationStatus.retired.equals(currentVersion.getStatus())) {
      log.warn("Version '{}' of codesystem '{}' is already retired, skipping retirement process.", version, codeSystem);
      return;
    }
    repository.retire(codeSystem, version);
  }

  @Transactional
  public void save(List<CodeSystemVersion> versions, String codeSystem) {
    repository.retainVersions(versions, codeSystem);
    if (versions != null) {
      versions.forEach(this::save);
    }
  }

  @Transactional
  public void linkEntityVersion(String codeSystem, String codeSystemVersion, Long entityVersionId) {
    Optional<Long> versionId = getVersion(codeSystem, codeSystemVersion).map(CodeSystemVersion::getId);
    if (versionId.isPresent()) {
      repository.linkEntityVersion(versionId.get(), entityVersionId);
    } else {
      throw ApiError.TE202.toApiException(Map.of("version", codeSystemVersion, "codeSystem", codeSystem));
    }
  }

  @Transactional
  public void unlinkEntityVersion(String codeSystem, String codeSystemVersion, Long entityVersionId) {
    Optional<Long> versionId = getVersion(codeSystem, codeSystemVersion).map(CodeSystemVersion::getId);
    if (versionId.isPresent()) {
      repository.unlinkEntityVersion(versionId.get(), entityVersionId);
    } else {
      throw ApiError.TE202.toApiException(Map.of("version", codeSystemVersion, "codeSystem", codeSystem));
    }
  }

  @Transactional
  public void saveEntityVersions(Long codeSystemVersionId, List<CodeSystemEntityVersion> entityVersions) {
    repository.unlinkEntityVersions(entityVersions, codeSystemVersionId);
    repository.linkEntityVersions(entityVersions, codeSystemVersionId);
  }
}
