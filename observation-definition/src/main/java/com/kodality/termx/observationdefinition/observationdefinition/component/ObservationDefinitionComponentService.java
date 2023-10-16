package com.kodality.termx.observationdefinition.observationdefinition.component;

import com.kodality.termx.observationdefintion.ObservationDefinitionComponent;
import java.util.List;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class ObservationDefinitionComponentService {
  private final ObservationDefinitionComponentRepository repository;

  @Transactional
  public void save(List<ObservationDefinitionComponent> components, Long observationDefinitionId, String type) {
    repository.retain(components, observationDefinitionId, type);
    if (components != null) {
      components.forEach(component -> repository.save(component, observationDefinitionId, type));
    }
  }

  public List<ObservationDefinitionComponent> load(Long observationDefinitionId, String type) {
    return repository.load(observationDefinitionId, type);
  }
}
