package com.kodality.termserver.codesystem;

import com.kodality.termserver.ApiError;
import com.kodality.termserver.PublicationStatus;
import jakarta.inject.Singleton;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@Slf4j
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
      throw ApiError.TE102.toApiException(Map.of("version", lastDraftVersion.getVersion(), "codeSystem", version.getCodeSystem()));
    }
    version.setCreated(version.getCreated() == null ? OffsetDateTime.now() : version.getCreated());
    repository.save(version);
  }

  public Optional<CodeSystemVersion> getVersion(String codeSystem, String versionCode) {
    return Optional.ofNullable(repository.getVersion(codeSystem, versionCode));
  }

  public List<CodeSystemVersion> getVersions(String codeSystem) {
    return repository.getVersions(codeSystem);
  }

  public String getLastActiveVersion(String codeSystem) {
    return repository.getLastActiveVersion(codeSystem);
  }

  @Transactional
  public void activateVersion(String codeSystem, String version) {
    CodeSystemVersion currentVersion = repository.getVersion(codeSystem, version);
    if (currentVersion == null) {
      throw ApiError.TE104.toApiException(Map.of("version", version, "codeSystem", codeSystem));
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
      throw ApiError.TE104.toApiException(Map.of("version", overlappingVersion.getVersion()));
    }
    repository.activate(codeSystem, version);
  }

  @Transactional
  public void saveEntityVersions(Long codeSystemVersionId, List<CodeSystemEntityVersion> entityVersions) {
    repository.retainEntityVersions(entityVersions, codeSystemVersionId);
    repository.upsertEntityVersions(entityVersions, codeSystemVersionId);
  }
}
