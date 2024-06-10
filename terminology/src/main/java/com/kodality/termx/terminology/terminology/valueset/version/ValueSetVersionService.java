package com.kodality.termx.terminology.terminology.valueset.version;

import com.kodality.commons.model.QueryResult;
import com.kodality.termx.terminology.ApiError;
import com.kodality.termx.terminology.terminology.valueset.ruleset.ValueSetVersionRuleSetService;
import com.kodality.termx.ts.PublicationStatus;
import com.kodality.termx.ts.valueset.ValueSetVersion;
import com.kodality.termx.ts.valueset.ValueSetVersionQueryParams;
import com.kodality.termx.ts.valueset.ValueSetVersionReference;
import com.kodality.termx.ts.valueset.ValueSetVersionRuleSet;
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

  @Transactional
  public void save(ValueSetVersion version) {
    if (version.getId() == null) {
      version.setId(load(version.getValueSet(), version.getVersion()).map(ValueSetVersionReference::getId).orElse(null));
    }
    if (!PublicationStatus.draft.equals(version.getStatus()) && version.getId() == null) {
      throw ApiError.TE101.toApiException();
    }
    if (!PublicationStatus.draft.equals(version.getStatus())) {
      repository.saveExpirationDate(version);
      return;
    }
    prepare(version);
    ValueSetVersion existingVersion = repository.query(new ValueSetVersionQueryParams()
        .setValueSet(version.getValueSet())
        .setVersion(version.getVersion())).findFirst().orElse(null);
    if (existingVersion != null && !existingVersion.getId().equals(version.getId())) {
      throw ApiError.TE102.toApiException(Map.of("version", existingVersion.getVersion()));
    }
    version.setCreated(version.getCreated() == null ? OffsetDateTime.now() : version.getCreated());
    repository.save(version);
    valueSetVersionRuleSetService.save(version.getRuleSet(), version.getValueSet(), version.getVersion());
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
    ValueSetVersion currentVersion = repository.load(valueSet, version);
    if (currentVersion == null) {
      throw ApiError.TE401.toApiException(Map.of("version", version, "valueSet", valueSet));
    }
    if (PublicationStatus.active.equals(currentVersion.getStatus())) {
      log.warn("Version '{}' of valueSet '{}' is already activated, skipping activation process.", version, valueSet);
      return;
    }
    repository.saveStatus(valueSet, version, PublicationStatus.active);
  }

  @Transactional
  public void retire(String valueSet, String version) {
    ValueSetVersion currentVersion = repository.load(valueSet, version);
    if (currentVersion == null) {
      throw ApiError.TE301.toApiException(Map.of("version", version, "valueSet", valueSet));
    }
    if (PublicationStatus.retired.equals(currentVersion.getStatus())) {
      log.warn("Version '{}' of valueSet '{}' is already retired, skipping retirement process.", version, valueSet);
      return;
    }
    repository.saveStatus(valueSet, version, PublicationStatus.retired);
  }

  @Transactional
  public void saveAsDraft(String valueSet, String version) {
    ValueSetVersion currentVersion = repository.load(valueSet, version);
    if (currentVersion == null) {
      throw ApiError.TE301.toApiException(Map.of("version", version, "valueSet", valueSet));
    }
    if (PublicationStatus.draft.equals(currentVersion.getStatus())) {
      log.warn("Version '{}' of valueSet '{}' is already draft, skipping retirement process.", version, valueSet);
      return;
    }
    repository.saveStatus(valueSet, version, PublicationStatus.draft);
  }

  public ValueSetVersion loadLastVersionByUri(String uri) {
    return repository.loadLastVersionByUri(uri);
  }

  @Transactional
  public void cancel(Long id) {
    repository.cancel(id);
  }

  public ValueSetVersion loadPreviousVersion(String valueSet, String version) {
    return repository.loadPreviousVersion(valueSet, version);
  }
}
