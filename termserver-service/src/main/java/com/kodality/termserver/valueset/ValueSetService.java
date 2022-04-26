package com.kodality.termserver.valueset;

import com.kodality.commons.model.QueryResult;
import java.util.Optional;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class ValueSetService {
  private final ValueSetRepository repository;
  private final ValueSetVersionService valueSetVersionService;

  public Optional<ValueSet> get(String id) {
    return Optional.ofNullable(repository.load(id));
  }

  @Transactional
  public void create(ValueSet valueSet) {
    repository.create(valueSet);
  }

  public QueryResult<ValueSet> query(ValueSetQueryParams params) {
    QueryResult<ValueSet> valueSets = repository.query(params);
    if (params.isDecorated()) {
      valueSets.getData().forEach(this::decorate);
    }
    return valueSets;
  }

  private void decorate(ValueSet valueSet) {
    valueSet.setVersions(valueSetVersionService.getVersions(valueSet.getId()));
  }
}
