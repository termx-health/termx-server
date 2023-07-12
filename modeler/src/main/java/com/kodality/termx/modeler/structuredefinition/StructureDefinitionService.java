package com.kodality.termx.modeler.structuredefinition;

import com.kodality.commons.model.QueryResult;
import jakarta.inject.Singleton;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class StructureDefinitionService {
  private final StructureDefinitionRepository repository;

  public Optional<StructureDefinition> load(Long id) {
    return Optional.ofNullable(repository.load(id));
  }

  public QueryResult<StructureDefinition> query(StructureDefinitionQueryParams params) {
    return repository.query(params);
  }

  @Transactional
  public StructureDefinition save(StructureDefinition structureDefinition) {
    repository.save(structureDefinition);
    return load(structureDefinition.getId()).orElse(null);
  }

  @Transactional
  public void cancel(Long id) {
    repository.cancel(id);
  }
}