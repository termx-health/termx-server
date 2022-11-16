package com.kodality.termserver.ts.valueset;

import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.auth.auth.UserPermissionService;
import com.kodality.termserver.ts.valueset.ruleset.ValueSetVersionRuleService;
import com.kodality.termserver.valueset.ValueSet;
import com.kodality.termserver.valueset.ValueSetQueryParams;
import com.kodality.termserver.valueset.ValueSetTransactionRequest;
import com.kodality.termserver.valueset.ValueSetVersionQueryParams;
import io.micronaut.core.util.CollectionUtils;
import java.time.LocalDate;
import java.util.Optional;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class ValueSetService {
  private final ValueSetRepository repository;
  private final ValueSetVersionService valueSetVersionService;
  private final ValueSetVersionRuleService valueSetVersionRuleService;

  private final UserPermissionService userPermissionService;

  public Optional<ValueSet> load(String id) {
    return Optional.ofNullable(repository.load(id));
  }

  @Transactional
  public void save(ValueSet valueSet) {
    userPermissionService.checkPermitted(valueSet.getId(), "ValueSet", "edit");
    repository.save(valueSet);
  }

  @Transactional
  public void save(ValueSetTransactionRequest request) {
    ValueSet valueSet = request.getValueSet();
    userPermissionService.checkPermitted(valueSet.getId(), "ValueSet", "edit");
    repository.save(valueSet);

    request.getVersion().setValueSet(valueSet.getId());
    request.getVersion().setReleaseDate(request.getVersion().getReleaseDate() == null ? LocalDate.now() : request.getVersion().getReleaseDate());
    valueSetVersionService.save(request.getVersion());
    if (request.getVersion().getRuleSet() != null && CollectionUtils.isNotEmpty(request.getVersion().getRuleSet().getRules())) {
      valueSetVersionRuleService.save(request.getVersion().getRuleSet().getRules(), request.getVersion().getRuleSet().getId(), valueSet.getId());
    }
  }

  public QueryResult<ValueSet> query(ValueSetQueryParams params) {
    QueryResult<ValueSet> valueSets = repository.query(params);
    if (params.isDecorated()) {
      valueSets.getData().forEach(this::decorate);
    }
    return valueSets;
  }

  private void decorate(ValueSet valueSet) {
    ValueSetVersionQueryParams params = new ValueSetVersionQueryParams();
    params.setValueSet(valueSet.getId());
    params.all();
    valueSet.setVersions(valueSetVersionService.query(params).getData());
  }

  @Transactional
  public void cancel(String valueSet) {
    userPermissionService.checkPermitted(valueSet, "ValueSet", "publish");
    repository.cancel(valueSet);
  }
}
