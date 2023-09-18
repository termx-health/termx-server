package com.kodality.termx.modeler.transformationdefinition;

import com.kodality.commons.model.QueryResult;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Singleton
public class TransformationDefinitionService {
  private final TransformationDefinitionRepository repository;

  public TransformationDefinition load(Long id) {
    return repository.load(id);
  }

  public QueryResult<TransformationDefinition> search(TransformationDefinitionQueryParams params) {
    return repository.search(params);
  }

  public void save(TransformationDefinition def) {
    repository.save(def);
  }

  @Transactional
  public TransformationDefinition duplicate(Long id) {
    TransformationDefinition def = load(id);
    def.setId(null);
    def.setName(def.getName() + "_duplicate");
    save(def);
    return def;
  }

  public void delete(Long id) {
    repository.delete(id);
  }
}
