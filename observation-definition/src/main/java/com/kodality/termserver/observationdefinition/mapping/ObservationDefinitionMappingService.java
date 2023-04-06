package com.kodality.termserver.observationdefinition.mapping;

import com.kodality.termserver.observationdefintion.ObservationDefinitionMapping;
import java.util.List;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class ObservationDefinitionMappingService {
  private final ObservationDefinitionMappingRepository repository;

  @Transactional
  public void save(List<ObservationDefinitionMapping> mappings, Long observationDefinitionId) {
    repository.retain(mappings, observationDefinitionId);
    if (mappings != null) {
      mappings.forEach(mapping -> repository.save(mapping, observationDefinitionId));
    }
  }

  public List<ObservationDefinitionMapping> load(Long observationDefinitionId) {
    return repository.load(observationDefinitionId);
  }
}
