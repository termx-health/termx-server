package com.kodality.termx.modeler.transformationdefinition;

import com.kodality.commons.model.QueryResult;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Singleton
public class TransformationDefinitionService {
  private final TransformationDefinitionRepository repository;

  public void save(TransformationDefinition def) {
    repository.save(def);
  }

  public TransformationDefinition load(Long id) {
    return repository.load(id);
  }

  public QueryResult<TransformationDefinition> search(TransformationDefinitionQueryParams params) {
    return repository.search(params);
  }

  public void delete(Long id) {
    repository.delete(id);
  }
}
