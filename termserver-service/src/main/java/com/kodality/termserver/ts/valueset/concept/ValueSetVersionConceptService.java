package com.kodality.termserver.ts.valueset.concept;

import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.ApiError;
import com.kodality.termserver.PublicationStatus;
import com.kodality.termserver.auth.auth.UserPermissionService;
import com.kodality.termserver.ts.codesystem.concept.ConceptService;
import com.kodality.termserver.ts.codesystem.designation.DesignationService;
import com.kodality.termserver.ts.valueset.ValueSetVersionRepository;
import com.kodality.termserver.valueset.ValueSetVersionConceptQueryParams;
import com.kodality.termserver.valueset.ValueSetVersionRuleSet;
import com.kodality.termserver.valueset.ValueSetVersion;
import com.kodality.termserver.valueset.ValueSetVersionConcept;
import io.micronaut.core.util.CollectionUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class ValueSetVersionConceptService {
  private final ConceptService conceptService;
  private final DesignationService designationService;
  private final ValueSetVersionConceptRepository repository;
  private final ValueSetVersionRepository valueSetVersionRepository;

  private final UserPermissionService userPermissionService;

  public Optional<ValueSetVersionConcept> load(Long id) {
    return Optional.ofNullable(repository.load(id));
  }

  public List<ValueSetVersionConcept> loadAll(Long valueSetVersionId) {
    return repository.loadAll(valueSetVersionId);
  }

  @Transactional
  public void save(List<ValueSetVersionConcept> concepts, Long valueSetVersionId) {
    repository.retain(concepts, valueSetVersionId);
    if (concepts != null) {
      concepts.forEach(concept -> save(concept, valueSetVersionId));
    }
  }

  @Transactional
  public void save(ValueSetVersionConcept concept, String valueSet, String valueSetVersion) {
    userPermissionService.checkPermitted(valueSet, "ValueSet", "edit");

    ValueSetVersion version = valueSetVersionRepository.load(valueSet, valueSetVersion);
    if (version == null) {
      throw ApiError.TE301.toApiException(Map.of("version", valueSetVersion, "valueSet", valueSet));
    }
    repository.save(concept, version.getId());
  }

  @Transactional
  public void save(ValueSetVersionConcept concept, Long valueSetVersionId) {
    repository.save(concept, valueSetVersionId);
  }

  @Transactional
  public void delete(Long id, String valueSet) {
    userPermissionService.checkPermitted(valueSet, "ValueSet", "edit");
    repository.delete(id);
  }

  public List<ValueSetVersionConcept> expand(String valueSet, String valueSetVersion, ValueSetVersionRuleSet ruleSet) {
    if (ruleSet != null) {
      return repository.expand(ruleSet).stream().map(this::decorate).collect(Collectors.toList());
    }
    if (valueSet == null) {
      return new ArrayList<>();
    }
    ValueSetVersion version = valueSetVersion == null ?
        valueSetVersionRepository.loadLastVersion(valueSet, PublicationStatus.active) :
        valueSetVersionRepository.load(valueSet, valueSetVersion);
    if (version == null) {
      return new ArrayList<>();
    }
    return repository.expand(version.getId()).stream().map(this::decorate).collect(Collectors.toList());
  }

  private ValueSetVersionConcept decorate(ValueSetVersionConcept c) {
    c.setDisplay(c.getDisplay() == null || c.getDisplay().getId() == null ? c.getDisplay() : designationService.load(c.getDisplay().getId()).orElse(c.getDisplay()));
    c.setConcept(c.getConcept() == null || c.getConcept().getId() == null ? c.getConcept() : conceptService.load(c.getConcept().getId()).orElse(c.getConcept()));
    if (CollectionUtils.isNotEmpty(c.getAdditionalDesignations())) {
      c.setAdditionalDesignations(c.getAdditionalDesignations().stream()
          .map(d -> d.getId() == null ? d : designationService.load(d.getId()).orElse(d))
          .collect(Collectors.toList()));
    }
    return c;
  }

  public QueryResult<ValueSetVersionConcept> query(ValueSetVersionConceptQueryParams params) {
    if (params.getValueSetVersionId() == null) {
      return QueryResult.empty();
    }
    QueryResult<ValueSetVersionConcept> concepts = repository.query(params);
    if (params.isDecorated()) {
      concepts.getData().forEach(this::decorate);
    }
    return concepts;
  }
}
