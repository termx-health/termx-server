package com.kodality.termx.terminology.terminology.definedproperty;

import com.kodality.commons.model.QueryResult;
import com.kodality.termx.ts.property.DefinedProperty;
import com.kodality.termx.ts.property.DefinedPropertyQueryParams;
import java.util.Optional;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class DefinedPropertyService {
  private final DefinedPropertyRepository repository;

  public Optional<DefinedProperty> load(Long id) {
    return Optional.ofNullable(repository.load(id));
  }

  public QueryResult<DefinedProperty> query(DefinedPropertyQueryParams params) {
    return repository.query(params);
  }

  @Transactional
  public void save(DefinedProperty entityProperty) {
    repository.save(entityProperty);
  }

}
