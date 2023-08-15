package com.kodality.termx.terminology.codesystem.definedentityproperty;

import com.kodality.commons.model.QueryResult;
import com.kodality.termx.ts.codesystem.DefinedEntityProperty;
import com.kodality.termx.ts.codesystem.DefinedEntityPropertyQueryParams;
import java.util.Optional;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class DefinedEntityPropertyService {
  private final DefinedEntityPropertyRepository repository;

  public Optional<DefinedEntityProperty> load(Long id) {
    return Optional.ofNullable(repository.load(id));
  }

  public QueryResult<DefinedEntityProperty> query(DefinedEntityPropertyQueryParams params) {
    return repository.query(params);
  }

  @Transactional
  public void save(DefinedEntityProperty entityProperty) {
    repository.save(entityProperty);
  }

}
