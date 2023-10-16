package com.kodality.termx.observationdefinition.observationdefinition.interpretation;

import com.kodality.termx.observationdefintion.ObservationDefinitionInterpretation;
import java.util.List;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class ObservationDefinitionInterpretationService {
  private final ObservationDefinitionInterpretationRepository repository;

  @Transactional
  public void save(List<ObservationDefinitionInterpretation> interpretations, Long observationDefinitionId) {
    repository.retain(interpretations, observationDefinitionId);
    if (interpretations != null) {
      interpretations.forEach(interpretation -> repository.save(interpretation, observationDefinitionId));
    }
  }

  public List<ObservationDefinitionInterpretation> load(Long observationDefinitionId) {
    return repository.load(observationDefinitionId);
  }
}
