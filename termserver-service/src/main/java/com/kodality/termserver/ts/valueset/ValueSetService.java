package com.kodality.termserver.ts.valueset;

import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.auth.auth.UserPermissionService;
import com.kodality.termserver.valueset.ValueSet;
import com.kodality.termserver.valueset.ValueSetQueryParams;
import com.kodality.termserver.valueset.ValueSetVersionQueryParams;
import java.util.Optional;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class ValueSetService {
  private final ValueSetRepository repository;
  private final ValueSetVersionService valueSetVersionService;

  private final UserPermissionService userPermissionService;

  public Optional<ValueSet> load(String id) {
    return Optional.ofNullable(repository.load(id));
  }

  @Transactional
  public void save(ValueSet valueSet) {
    userPermissionService.checkPermitted(valueSet.getId(), "ValueSet", "edit");
    repository.save(valueSet);
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
}
