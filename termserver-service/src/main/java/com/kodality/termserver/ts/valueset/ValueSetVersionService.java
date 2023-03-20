package com.kodality.termserver.ts.valueset;

import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.ApiError;
import com.kodality.termserver.ts.PublicationStatus;
import com.kodality.termserver.auth.auth.UserPermissionService;
import com.kodality.termserver.ts.valueset.concept.ValueSetVersionConceptService;
import com.kodality.termserver.ts.valueset.ruleset.ValueSetVersionRuleSetService;
import java.time.OffsetDateTime;
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
  private final ValueSetVersionRuleSetService valueSetVersionRuleSetService;
  private final ValueSetVersionConceptService valueSetVersionConceptService;

  private final UserPermissionService userPermissionService;

  @Transactional
  public void save(ValueSetVersion version) {
    userPermissionService.checkPermitted(version.getValueSet(), "ValueSet", "edit");

    if (!PublicationStatus.draft.equals(version.getStatus()) && version.getId() == null) {
      throw ApiError.TE101.toApiException();
    }
    if (!PublicationStatus.draft.equals(version.getStatus())) {
      repository.saveExpirationDate(version);
      return;
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
    valueSetVersionRuleSetService.save(version.getRuleSet(), version.getId(), version.getValueSet());
  }

  public ValueSetVersion load(Long id) {
    return decorate(repository.load(id));
  }

  public Optional<ValueSetVersion> load(String valueSet, String versionCode) {
    return Optional.ofNullable(decorate(repository.load(valueSet, versionCode)));
  }

  public ValueSetVersion loadLastVersion(String valueSet) {
    return decorate(repository.loadLastVersion(valueSet));
  }

  public QueryResult<ValueSetVersion> query(ValueSetVersionQueryParams params) {
    QueryResult<ValueSetVersion> versions = repository.query(params);
    if (params.isDecorated()) {
      versions.getData().forEach(this::decorate);
    }
    return versions;
  }

  @Transactional
  public void activate(String valueSet, String version) {
    userPermissionService.checkPermitted(valueSet, "ValueSet", "publish");

    ValueSetVersion currentVersion = repository.load(valueSet, version);
    if (currentVersion == null) {
      throw ApiError.TE401.toApiException(Map.of("version", version, "valueSet", valueSet));
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
    userPermissionService.checkPermitted(valueSet, "ValueSet", "publish");

    ValueSetVersion currentVersion = repository.load(valueSet, version);
    if (currentVersion == null) {
      throw ApiError.TE301.toApiException(Map.of("version", version, "valueSet", valueSet));
    }
    if (PublicationStatus.retired.equals(currentVersion.getStatus())) {
      log.warn("Version '{}' of valueSet '{}' is already retired, skipping retirement process.", version, valueSet);
      return;
    }
    repository.retire(valueSet, version);
  }

  @Transactional
  public void saveAsDraft(String valueSet, String version) {
    userPermissionService.checkPermitted(valueSet, "ValueSet", "publish");

    ValueSetVersion currentVersion = repository.load(valueSet, version);
    if (currentVersion == null) {
      throw ApiError.TE301.toApiException(Map.of("version", version, "valueSet", valueSet));
    }
    if (PublicationStatus.draft.equals(currentVersion.getStatus())) {
      log.warn("Version '{}' of valueSet '{}' is already draft, skipping retirement process.", version, valueSet);
      return;
    }
    repository.saveAsDraft(valueSet, version);
  }

  private ValueSetVersion decorate(ValueSetVersion version) {
    if (version == null) {
      return null;
    }
    version.setConcepts(valueSetVersionConceptService.loadAll(version.getId()));
    version.setRuleSet(valueSetVersionRuleSetService.load(version.getId()).orElse(null));
    return version;
  }

  public ValueSetVersion loadLastVersionByUri(String uri) {
    return decorate(repository.loadLastVersionByUri(uri));
  }

  @Transactional
  public void cancel(Long id, String valueSet) {
    userPermissionService.checkPermitted(valueSet, "ValueSet", "publish");
    repository.cancel(id);
  }
}
