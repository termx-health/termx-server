package com.kodality.termserver.terminology.valueset;

import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.ApiError;
import com.kodality.termserver.auth.UserPermissionService;
import com.kodality.termserver.terminology.valueset.ruleset.ValueSetVersionRuleSetService;
import com.kodality.termserver.ts.PublicationStatus;
import com.kodality.termserver.ts.valueset.ValueSetVersion;
import com.kodality.termserver.ts.valueset.ValueSetVersionQueryParams;
import com.kodality.termserver.ts.valueset.ValueSetVersionReference;
import com.kodality.termserver.ts.valueset.ValueSetVersionRuleSet;
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

  private final UserPermissionService userPermissionService;

  @Transactional
  public void save(ValueSetVersion version) {
    userPermissionService.checkPermitted(version.getValueSet(), "ValueSet", "edit");

    version.setId(load(version.getValueSet(), version.getVersion()).map(ValueSetVersionReference::getId).orElse(null));
    if (!PublicationStatus.draft.equals(version.getStatus()) && version.getId() == null) {
      throw ApiError.TE101.toApiException();
    }
    if (!PublicationStatus.draft.equals(version.getStatus())) {
      repository.saveExpirationDate(version);
      return;
    }
    prepare(version);
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

  private void prepare(ValueSetVersion version) {
    if (version.getRuleSet() == null) {
      version.setRuleSet(new ValueSetVersionRuleSet());
    }
  }

  public ValueSetVersion load(Long id) {
    return repository.load(id);
  }

  public Optional<ValueSetVersion> load(String valueSet, String versionCode) {
    return Optional.ofNullable(repository.load(valueSet, versionCode));
  }

  public ValueSetVersion loadLastVersion(String valueSet) {
    return repository.loadLastVersion(valueSet);
  }

  public QueryResult<ValueSetVersion> query(ValueSetVersionQueryParams params) {
    return repository.query(params);
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

  public ValueSetVersion loadLastVersionByUri(String uri) {
    return repository.loadLastVersionByUri(uri);
  }

  @Transactional
  public void cancel(Long id, String valueSet) {
    userPermissionService.checkPermitted(valueSet, "ValueSet", "publish");
    repository.cancel(id);
  }
}
