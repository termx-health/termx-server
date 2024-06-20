package com.kodality.termx.terminology.terminology.valueset;

import com.kodality.commons.model.QueryResult;
import com.kodality.termx.terminology.ApiError;
import com.kodality.termx.core.fhir.BaseFhirMapper;
import com.kodality.termx.terminology.terminology.valueset.ruleset.ValueSetVersionRuleService;
import com.kodality.termx.terminology.terminology.valueset.version.ValueSetVersionService;
import com.kodality.termx.ts.valueset.ValueSet;
import com.kodality.termx.ts.valueset.ValueSetQueryParams;
import com.kodality.termx.ts.valueset.ValueSetTransactionRequest;
import com.kodality.termx.ts.valueset.ValueSetVersion;
import com.kodality.termx.ts.valueset.ValueSetVersionQueryParams;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class ValueSetService {
  private final ValueSetRepository repository;
  private final ValueSetVersionService valueSetVersionService;
  private final ValueSetVersionRuleService valueSetVersionRuleService;

  public ValueSet load(String id) {
    return repository.load(id);
  }

  public Optional<ValueSet> load(String valueSet, boolean decorate) {
    return Optional.ofNullable(repository.load(valueSet)).map(vs -> decorate ? decorate(vs) : vs);
  }

  @Transactional
  public void save(ValueSet valueSet) {
    validate(valueSet);
    repository.save(valueSet);
  }

  @Transactional
  public void save(ValueSetTransactionRequest request) {
    ValueSet valueSet = request.getValueSet();
    validate(valueSet);
    repository.save(valueSet);

    ValueSetVersion version = request.getVersion();
    if (version != null) {
      version.setValueSet(valueSet.getId());
      version.setReleaseDate(version.getReleaseDate() == null ? LocalDate.now() : version.getReleaseDate());
      valueSetVersionService.save(version);

      if (CollectionUtils.isNotEmpty(version.getRuleSet().getRules())) {
        valueSetVersionRuleService.save(version.getRuleSet().getRules(), valueSet.getId(), version.getVersion());
      }
    }
  }

  public QueryResult<ValueSet> query(ValueSetQueryParams params) {
    QueryResult<ValueSet> valueSets = repository.query(params);
    if (params.isDecorated()) {
      valueSets.getData().forEach(this::decorate);
    }
    return valueSets;
  }

  private ValueSet decorate(ValueSet valueSet) {
    ValueSetVersionQueryParams params = new ValueSetVersionQueryParams();
    params.setValueSet(valueSet.getId());
    params.all();
    valueSet.setVersions(valueSetVersionService.query(params).getData());
    return valueSet;
  }

  @Transactional
  public void cancel(String valueSet) {
    List<String> requiredCodeSystems = List.of("codesystem-content-mode", "concept-property-type", "contact-point-system", "contact-point-use",
        "filter-operator", "languages", "namingsystem-identifier-type", "namingsystem-type", "publication-status", "publisher");
    if (requiredCodeSystems.contains(valueSet)) {
      throw ApiError.TE303.toApiException();
    }
    repository.cancel(valueSet);
  }

  @Transactional
  public void changeId(String currentId, String newId) {
    validateId(newId);
    repository.changeId(currentId, newId);
  }

  private void validateId(String id) {
    if (id.contains(BaseFhirMapper.SEPARATOR)) {
      throw ApiError.TE113.toApiException(Map.of("symbols", BaseFhirMapper.SEPARATOR));
    }
  }

  private void validate(ValueSet valueSet) {
    validateId(valueSet.getId());

    if (CollectionUtils.isNotEmpty(valueSet.getConfigurationAttributes())) {
      valueSet.getConfigurationAttributes().forEach(c -> {
        if (StringUtils.isEmpty(c.getValue())) {
          throw ApiError.TE117.toApiException(Map.of("valueSet", valueSet.getId()));
        }
      });
    }
  }
}
