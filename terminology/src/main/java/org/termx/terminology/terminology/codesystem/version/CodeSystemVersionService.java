package org.termx.terminology.terminology.codesystem.version;

import com.kodality.commons.model.QueryResult;
import jakarta.inject.Provider;
import org.termx.core.ts.UcumSearchCacheInvalidator;
import org.termx.terminology.ApiError;
import org.termx.terminology.terminology.codesystem.CodeSystemRepository;
import org.termx.terminology.terminology.codesystem.entity.CodeSystemEntityVersionService;
import org.termx.terminology.terminology.valueset.ValueSetCodeSystemImpactService;
import org.termx.ts.PublicationStatus;
import org.termx.ts.codesystem.CodeSystem;
import org.termx.ts.codesystem.CodeSystemEntityVersion;
import org.termx.ts.codesystem.CodeSystemEntityVersionQueryParams;
import org.termx.ts.codesystem.CodeSystemVersion;
import org.termx.ts.codesystem.CodeSystemVersionQueryParams;
import org.termx.ts.codesystem.CodeSystemVersionReference;
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
  private final CodeSystemEntityVersionService entityVersionService;
  private final Provider<ValueSetCodeSystemImpactService> valueSetCodeSystemImpactServiceProvider;
  private final CodeSystemRepository codeSystemRepository;
  private final UcumSearchCacheInvalidator ucumSearchCacheInvalidator;

  @Transactional
  public void save(CodeSystemVersion version) {
    if (version.getId() == null) {
      version.setId(load(version.getCodeSystem(), version.getVersion()).map(CodeSystemVersionReference::getId).orElse(null));
    }
    if (!PublicationStatus.draft.equals(version.getStatus()) && version.getId() == null) {
      throw ApiError.TE101.toApiException();
    }
    if (!PublicationStatus.draft.equals(version.getStatus())) {
      repository.saveExpirationDate(version);
      return;
    }
    CodeSystemVersion existingVersion = repository.query(new CodeSystemVersionQueryParams()
        .setCodeSystem(version.getCodeSystem())
        .setVersion(version.getVersion())).findFirst().orElse(null);
    if (existingVersion != null && !existingVersion.getId().equals(version.getId())) {
      throw ApiError.TE102.toApiException(Map.of("version", existingVersion.getVersion()));
    }
    version.setCreated(version.getCreated() == null ? OffsetDateTime.now() : version.getCreated());
    repository.save(version);
    invalidateCaches(version.getCodeSystem());
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

  public CodeSystemVersion loadPreviousVersion(String codeSystem, String version) {
    return repository.loadPreviousVersion(codeSystem, version);
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
    CodeSystemVersion currentVersion = repository.load(codeSystem, version);
    if (currentVersion == null) {
      throw ApiError.TE202.toApiException(Map.of("version", version, "codeSystem", codeSystem));
    }
    if (PublicationStatus.active.equals(currentVersion.getStatus())) {
      log.warn("Version '{}' of codesystem '{}' is already activated, skipping activation process.", version, codeSystem);
      return;
    }
    repository.activate(codeSystem, version);
    entityVersionService.activate(codeSystem, version);
    valueSetCodeSystemImpactServiceProvider.get().refreshDynamicValueSets(codeSystem);
    invalidateCaches(codeSystem);
  }

  @Transactional
  public void retire(String codeSystem, String version) {
    CodeSystemVersion currentVersion = repository.load(codeSystem, version);
    if (currentVersion == null) {
      throw ApiError.TE202.toApiException(Map.of("version", version, "codeSystem", codeSystem));
    }
    if (PublicationStatus.retired.equals(currentVersion.getStatus())) {
      log.warn("Version '{}' of codesystem '{}' is already retired, skipping retirement process.", version, codeSystem);
      return;
    }
    repository.retire(codeSystem, version);
    invalidateCaches(codeSystem);
  }

  @Transactional
  public void saveAsDraft(String codeSystem, String version) {
    CodeSystemVersion currentVersion = repository.load(codeSystem, version);
    if (currentVersion == null) {
      throw ApiError.TE202.toApiException(Map.of("version", version, "codeSystem", codeSystem));
    }
    if (PublicationStatus.draft.equals(currentVersion.getStatus())) {
      log.warn("Version '{}' of codesystem '{}' is already draft, skipping retirement process.", version, codeSystem);
      return;
    }
    repository.saveAsDraft(codeSystem, version);
    invalidateCaches(codeSystem);
  }

  @Transactional
  public void save(List<CodeSystemVersion> versions, String codeSystem) {
    repository.retainVersions(versions, codeSystem);
    if (versions != null) {
      versions.forEach(this::save);
    }
  }

  @Transactional
  public void linkEntityVersions(String codeSystem, String codeSystemVersion, List<Long> entityVersionIds) {
    Long versionId = load(codeSystem, codeSystemVersion).map(CodeSystemVersion::getId)
        .orElseThrow(() -> ApiError.TE202.toApiException(Map.of("version", codeSystemVersion, "codeSystem", codeSystem)));
    linkEntityVersions(versionId, entityVersionIds);
  }

  @Transactional
  public void unlinkEntityVersions(String codeSystem, String codeSystemVersion, List<Long> entityVersionIds) {
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
    repository.cancel(id);
    invalidateCaches(codeSystem);
  }

  private void invalidateCaches(String codeSystem) {
    invalidateIfUcumRelated(codeSystem);
  }

  private void invalidateIfUcumRelated(String codeSystem) {
    if ("ucum".equals(codeSystem)) {
      ucumSearchCacheInvalidator.invalidate();
      return;
    }
    CodeSystem current = codeSystemRepository.load(codeSystem);
    if (current != null && "ucum".equals(current.getBaseCodeSystem())) {
      ucumSearchCacheInvalidator.invalidate();
    }
  }
}
