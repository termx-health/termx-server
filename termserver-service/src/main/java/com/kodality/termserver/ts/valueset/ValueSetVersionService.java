package com.kodality.termserver.ts.valueset;

import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.ApiError;
import com.kodality.termserver.PublicationStatus;
import com.kodality.termserver.codesystem.Concept;
import com.kodality.termserver.codesystem.Designation;
import com.kodality.termserver.ts.codesystem.concept.ConceptService;
import com.kodality.termserver.ts.codesystem.designation.DesignationService;
import com.kodality.termserver.valueset.ValueSetConcept;
import com.kodality.termserver.valueset.ValueSetRuleSet;
import com.kodality.termserver.valueset.ValueSetRuleSet.ValueSetRule;
import com.kodality.termserver.valueset.ValueSetVersion;
import com.kodality.termserver.valueset.ValueSetVersionQueryParams;
import io.micronaut.core.util.CollectionUtils;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class ValueSetVersionService {
  private final ConceptService conceptService;
  private final DesignationService designationService;

  private final ValueSetVersionRepository repository;
  private final ValueSetVersionConceptRepository valueSetVersionConceptRepository;

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
    repository.save(prepare(version));
  }

  public ValueSetVersion getVersion(Long id) {
    return decorate(repository.getVersion(id));
  }

  public List<ValueSetVersion> getVersions(String valueSet) {
    return repository.getVersions(valueSet);
  }

  public Optional<ValueSetVersion> getVersion(String valueSet, String versionCode) {
    return Optional.ofNullable(decorate(repository.getVersion(valueSet, versionCode)));
  }

  public Optional<ValueSetVersion> getLastVersion(String valueSet, String status) {
    return Optional.ofNullable(decorate(repository.getLastVersion(valueSet, status)));
  }

  public QueryResult<ValueSetVersion> query(ValueSetVersionQueryParams params) {
    return repository.query(params);
  }

  @Transactional
  public void activate(String valueSet, String version) {
    ValueSetVersion currentVersion = repository.getVersion(valueSet, version);
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
    ValueSetVersion currentVersion = repository.getVersion(valueSet, version);
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
  public void saveConcepts(String valueSet, String valueSetVersion, List<ValueSetConcept> concepts) {
    Optional<Long> versionId = getVersion(valueSet, valueSetVersion).map(ValueSetVersion::getId);
    if (versionId.isPresent()) {
      saveConcepts(versionId.get(), concepts);
    } else {
      throw ApiError.TE301.toApiException(Map.of("version", valueSetVersion, "valueSet", valueSet));
    }
  }

  public List<ValueSetConcept> getConcepts(String valueSet, String version) {
    Optional<ValueSetVersion> vsVersion = getVersion(valueSet, version);
    if (vsVersion.isEmpty()) {
      throw ApiError.TE301.toApiException(Map.of("version", version, "valueSet", valueSet));
    }
    List<ValueSetConcept> concepts = valueSetVersionConceptRepository.getConcepts(vsVersion.get().getId());
    decorate(concepts);
    return concepts;
  }

  @Transactional
  public void saveConcepts(Long valueSetVersionId, List<ValueSetConcept> concepts) {
    valueSetVersionConceptRepository.retainConcepts(concepts, valueSetVersionId);
    valueSetVersionConceptRepository.upsertConcepts(concepts, valueSetVersionId);
  }

  public List<ValueSetConcept> expand(String valueSet) {
    Optional<ValueSetVersion> lastActiveVersion = getLastVersion(valueSet, PublicationStatus.active);
    if (lastActiveVersion.isPresent()) {
      return decorate(valueSetVersionConceptRepository.expand(valueSet, lastActiveVersion.get().getVersion(), null));
    } else {
      throw ApiError.TE302.toApiException(Map.of("valueSet", valueSet));
    }
  }

  public List<ValueSetConcept> expand(String valueSet, String versionCode) {
    return decorate(valueSetVersionConceptRepository.expand(valueSet, versionCode, null));
  }

  public List<ValueSetConcept> expand(ValueSetRuleSet ruleSet) {
    List<ValueSetConcept> concepts = new ArrayList<>();
    if (ruleSet == null) {
      return concepts;
    }
    return decorate(valueSetVersionConceptRepository.expand(null, null, ruleSet));
  }

  private ValueSetVersion decorate(ValueSetVersion version) {
    if (version != null && version.getRuleSet() != null) {
      if (CollectionUtils.isNotEmpty(version.getRuleSet().getIncludeRules())) {
        version.getRuleSet().getIncludeRules().forEach(r -> decorate(r.getConcepts()));
      }
      if (CollectionUtils.isNotEmpty(version.getRuleSet().getExcludeRules())) {
        version.getRuleSet().getExcludeRules().forEach(r -> decorate(r.getConcepts()));
      }
    }
    return version;
  }

  private List<ValueSetConcept> decorate(List<ValueSetConcept> concepts) {
    if (CollectionUtils.isNotEmpty(concepts)) {
      concepts.forEach(c -> {
        c.setDisplay(c.getDisplay() == null || c.getDisplay().getId() == null ? null : designationService.get(c.getDisplay().getId()).orElse(null));
        c.setConcept(c.getConcept() == null || c.getConcept().getId() == null ? null : conceptService.get(c.getConcept().getId()).orElse(null));
        if (CollectionUtils.isNotEmpty(c.getAdditionalDesignations())) {
          c.setAdditionalDesignations(c.getAdditionalDesignations().stream()
              .map(d -> d.getId() == null ? d : designationService.get(d.getId()).orElse(null))
              .filter(Objects::nonNull)
              .collect(Collectors.toList()));
        }
      });
    }
    return concepts;
  }

  private ValueSetVersion prepare(ValueSetVersion version) {
    if (version.getRuleSet() != null) {
      if (CollectionUtils.isNotEmpty(version.getRuleSet().getIncludeRules())) {
        version.getRuleSet().getIncludeRules().forEach(this::prepare);
      }
      if (CollectionUtils.isNotEmpty(version.getRuleSet().getExcludeRules())) {
        version.getRuleSet().getExcludeRules().forEach(this::prepare);
      }
    }
    return version;
  }

  private void prepare(ValueSetRule r) {
    if (CollectionUtils.isNotEmpty(r.getConcepts())) {
      r.getConcepts().forEach(c -> {
        c.setDisplay(c.getDisplay() == null ? null : new Designation().setId(c.getDisplay().getId()));
        if (c.getConcept() != null) {
          Concept concept = new Concept();
          concept.setId(c.getConcept().getId());
          c.setConcept(concept);
        }
        if (CollectionUtils.isNotEmpty(c.getAdditionalDesignations())) {
          c.setAdditionalDesignations(c.getAdditionalDesignations().stream().map(d -> new Designation().setId(d.getId())).collect(Collectors.toList()));
        }
      });
    }
  }
}
