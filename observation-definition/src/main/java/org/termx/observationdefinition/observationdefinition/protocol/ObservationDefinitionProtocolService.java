package org.termx.observationdefinition.observationdefinition.protocol;

import org.termx.observationdefinition.observationdefinition.component.ObservationDefinitionComponentService;
import org.termx.observationdefintion.ObservationDefinitionComponentSectionType;
import org.termx.observationdefintion.ObservationDefinitionProtocol;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class ObservationDefinitionProtocolService {
  private final ObservationDefinitionProtocolRepository repository;
  private final ObservationDefinitionComponentService componentService;

  @Transactional
  public void save(ObservationDefinitionProtocol protocol, Long observationDefinitionId) {
    repository.save(protocol, observationDefinitionId);
    componentService.save(protocol.getComponents(), observationDefinitionId, ObservationDefinitionComponentSectionType.protocol);
  }

  public ObservationDefinitionProtocol load(Long observationDefinitionId) {
    return decorate(repository.load(observationDefinitionId), observationDefinitionId);
  }

  private ObservationDefinitionProtocol decorate(ObservationDefinitionProtocol protocol, Long observationDefinitionId) {
    if (protocol == null) {
      return null;
    }
    protocol.setComponents(componentService.load(observationDefinitionId, ObservationDefinitionComponentSectionType.protocol));
    return protocol;
  }
}
