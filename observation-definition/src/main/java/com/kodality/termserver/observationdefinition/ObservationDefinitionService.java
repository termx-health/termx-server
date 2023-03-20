package com.kodality.termserver.observationdefinition;

import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.observationdefintion.ObservationDefinition;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class ObservationDefinitionService {
  private final ObservationDefinitionRepository repository;

  @Transactional
  public void save(ObservationDefinition def) {
    repository.save(def);
  }

  public ObservationDefinition load(Long id) {
    return repository.load(id);
  }

  public ObservationDefinition load(String code) {
    return repository.load(code);
  }

  public QueryResult<ObservationDefinition> search(ObservationDefinitionSearchParams params) {
    return repository.search(params);
  }
}
