package com.kodality.termx.observationdefinition.observationdefinition.value;

import com.kodality.termx.observationdefintion.ObservationDefinitionValue;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class ObservationDefinitionValueService {
  private final ObservationDefinitionValueRepository repository;

  @Transactional
  public void save(ObservationDefinitionValue value, Long observationDefinitionId) {
    if (value == null) {
      repository.cancel(observationDefinitionId);
      return;
    }
    repository.save(value, observationDefinitionId);
  }

  public ObservationDefinitionValue load(Long observationDefinitionId) {
    return repository.load(observationDefinitionId);
  }
}
