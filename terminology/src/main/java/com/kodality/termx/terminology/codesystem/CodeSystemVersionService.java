package com.kodality.termx.terminology.codesystem;

import com.kodality.commons.model.QueryResult;
import com.kodality.termx.ApiError;
import com.kodality.termx.auth.UserPermissionService;
import com.kodality.termx.ts.PublicationStatus;
import com.kodality.termx.ts.codesystem.CodeSystemVersion;
import com.kodality.termx.ts.codesystem.CodeSystemVersionQueryParams;
import com.kodality.termx.ts.codesystem.CodeSystemVersionReference;
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
  private final UserPermissionService userPermissionService;

  @Transactional
  public void save(CodeSystemVersion version) {
    userPermissionService.checkPermitted(version.getCodeSystem(), "CodeSystem", "edit");

    version.setId(load(version.getCodeSystem(), version.getVersion()).map(CodeSystemVersionReference::getId).orElse(null));

    if (!PublicationStatus.draft.equals(version.getStatus()) && version.getId() == null) {
      throw ApiError.TE101.toApiException();
    }
    if (!PublicationStatus.draft.equals(version.getStatus())) {
      repository.saveExpirationDate(version);
      return;
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

  public Optional<CodeSystemVersion> load(String codeSystem, String versionCode) {
    return Optional.ofNullable(repository.load(codeSystem, versionCode));
  }

  public CodeSystemVersion load(Long id) {
    return repository.load(id);
  }

  public CodeSystemVersion loadLastVersion(String codeSystem) {
    return repository.loadLastVersion(codeSystem);
  }

  public CodeSystemVersion loadLastVersionByUri(String uri) {
    return repository.loadLastVersionByUri(uri);
  }

  public Optional<CodeSystemVersion> loadVersionByUri(String uri, String versionCode) {
    CodeSystemVersionQueryParams p = new CodeSystemVersionQueryParams();
    p.setCodeSystemUri(uri);
    p.setVersion(versionCode);
    p.setLimit(1);
    return repository.query(p).findFirst();
  }

  public QueryResult<CodeSystemVersion> query(CodeSystemVersionQueryParams params) {
    return repository.query(params);
  }

  @Transactional
  public void activate(String codeSystem, String version) {
    userPermissionService.checkPermitted(codeSystem, "CodeSystem", "publish");
    CodeSystemVersion currentVersion = repository.load(codeSystem, version);
    if (currentVersion == null) {
      throw ApiError.TE202.toApiException(Map.of("version", version, "codeSystem", codeSystem));
    }
    if (PublicationStatus.active.equals(currentVersion.getStatus())) {
      log.warn("Version '{}' of codesystem '{}' is already activated, skipping activation process.", version, codeSystem);
      return;
    }

//    CodeSystemVersion overlappingVersion = repository.query(new CodeSystemVersionQueryParams()
//        .setCodeSystem(codeSystem)
//        .setStatus(PublicationStatus.active)
//        .setReleaseDateLe(currentVersion.getExpirationDate())
//        .setExpirationDateGe(currentVersion.getReleaseDate())).findFirst().orElse(null);
//    if (overlappingVersion != null) {
//      throw ApiError.TE103.toApiException(Map.of("version", overlappingVersion.getVersion()));
//    }
    repository.activate(codeSystem, version);
  }

  @Transactional
  public void retire(String codeSystem, String version) {
    userPermissionService.checkPermitted(codeSystem, "CodeSystem", "publish");

    CodeSystemVersion currentVersion = repository.load(codeSystem, version);
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
  public void saveAsDraft(String codeSystem, String version) {
    userPermissionService.checkPermitted(codeSystem, "CodeSystem", "publish");

    CodeSystemVersion currentVersion = repository.load(codeSystem, version);
    if (currentVersion == null) {
      throw ApiError.TE202.toApiException(Map.of("version", version, "codeSystem", codeSystem));
    }
    if (PublicationStatus.draft.equals(currentVersion.getStatus())) {
      log.warn("Version '{}' of codesystem '{}' is already draft, skipping retirement process.", version, codeSystem);
      return;
    }
    repository.saveAsDraft(codeSystem, version);
  }

  @Transactional
  public void save(List<CodeSystemVersion> versions, String codeSystem) {
    userPermissionService.checkPermitted(codeSystem, "CodeSystem", "edit");

    repository.retainVersions(versions, codeSystem);
    if (versions != null) {
      versions.forEach(this::save);
    }
  }

  @Transactional
  public void linkEntityVersions(String codeSystem, String codeSystemVersion, List<Long> entityVersionIds) {
    userPermissionService.checkPermitted(codeSystem, "CodeSystem", "edit");

    Long versionId = load(codeSystem, codeSystemVersion).map(CodeSystemVersion::getId)
        .orElseThrow(() -> ApiError.TE202.toApiException(Map.of("version", codeSystemVersion, "codeSystem", codeSystem)));
    linkEntityVersions(versionId, entityVersionIds);
  }

  @Transactional
  public void unlinkEntityVersions(String codeSystem, String codeSystemVersion, List<Long> entityVersionIds) {
    userPermissionService.checkPermitted(codeSystem, "CodeSystem", "edit");

    Long versionId = load(codeSystem, codeSystemVersion).map(CodeSystemVersion::getId)
        .orElseThrow(() -> ApiError.TE202.toApiException(Map.of("version", codeSystemVersion, "codeSystem", codeSystem)));
    unlinkEntityVersions(versionId, entityVersionIds);
  }

  @Transactional
  public void linkEntityVersions(Long codeSystemVersionId, List<Long> entityVersionIds) {
    repository.linkEntityVersions(entityVersionIds, codeSystemVersionId);
  }

  @Transactional
  public void unlinkEntityVersions(Long codeSystemVersionId, List<Long> entityVersionIds) {
    repository.unlinkEntityVersions(entityVersionIds, codeSystemVersionId);
  }

  @Transactional
  public void cancel(Long id, String codeSystem) {
    userPermissionService.checkPermitted(codeSystem, "CodeSystem", "publish");
    repository.cancel(id);
  }
}
