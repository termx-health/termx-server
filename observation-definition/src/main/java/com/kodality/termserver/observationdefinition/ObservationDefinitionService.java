package com.kodality.termserver.observationdefinition;

import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.ApiError;
import com.kodality.termserver.observationdefinition.component.ObservationDefinitionComponentService;
import com.kodality.termserver.observationdefinition.interpretation.ObservationDefinitionInterpretationService;
import com.kodality.termserver.observationdefinition.mapping.ObservationDefinitionMappingService;
import com.kodality.termserver.observationdefinition.member.ObservationDefinitionMemberService;
import com.kodality.termserver.observationdefinition.protocol.ObservationDefinitionProtocolService;
import com.kodality.termserver.observationdefinition.value.ObservationDefinitionValueService;
import com.kodality.termserver.observationdefintion.ObservationDefinition;
import com.kodality.termserver.observationdefintion.ObservationDefinitionComponentSectionType;
import com.kodality.termserver.observationdefintion.ObservationDefinitionSearchParams;
import com.kodality.termserver.observationdefintion.ObservationDefinitionStructure;
import io.micronaut.core.util.CollectionUtils;
import java.util.List;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class ObservationDefinitionService {
  private final ObservationDefinitionRepository repository;
  private final ObservationDefinitionValueService valueService;
  private final ObservationDefinitionMemberService memberService;
  private final ObservationDefinitionComponentService componentService;
  private final ObservationDefinitionProtocolService protocolService;
  private final ObservationDefinitionInterpretationService interpretationService;
  private final ObservationDefinitionMappingService mappingService;

  @Transactional
  public void save(ObservationDefinition def) {
    validate(def);
    prepare(def);
    repository.save(def);

    valueService.save(def.getValue(), def.getId());
    memberService.save(def.getMembers(), def.getId());
    componentService.save(def.getComponents(), def.getId(), ObservationDefinitionComponentSectionType.component);
    protocolService.save(def.getProtocol(), def.getId());
    componentService.save(def.getState(), def.getId(), ObservationDefinitionComponentSectionType.state);
    interpretationService.save(def.getInterpretations(), def.getId());
    mappingService.save(def.getMappings(), def.getId());
  }

  public ObservationDefinition load(Long id) {
    return decorate(repository.load(id));
  }

  public ObservationDefinition load(String code) {
    return decorate(repository.load(code));
  }

  public QueryResult<ObservationDefinition> search(ObservationDefinitionSearchParams params) {
    QueryResult<ObservationDefinition> definitions = repository.search(params);
    if (params.isDecorated()) {
      definitions.getData().forEach(this::decorate);
    }
    if (params.isDecoratedValue()) {
      definitions.getData().forEach(this::decorateValue);
    }
    return definitions;
  }

  private ObservationDefinition decorate(ObservationDefinition observationDefinition) {
    Long id = observationDefinition.getId();
    decorateValue(observationDefinition);
    observationDefinition.setMembers(memberService.load(id));
    observationDefinition.setComponents(componentService.load(id, ObservationDefinitionComponentSectionType.component));
    observationDefinition.setProtocol(protocolService.load(id));
    observationDefinition.setState(componentService.load(id, ObservationDefinitionComponentSectionType.state));
    observationDefinition.setInterpretations(interpretationService.load(id));
    observationDefinition.setMappings(mappingService.load(id));
    return observationDefinition;
  }

  private void decorateValue(ObservationDefinition observationDefinition) {
    observationDefinition.setValue(valueService.load(observationDefinition.getId()));
  }

  private void prepare(ObservationDefinition def) {
    if (!def.getStructure().contains(ObservationDefinitionStructure.value)) {
      def.setValue(null);
    }
    if (!def.getStructure().contains(ObservationDefinitionStructure.panel)) {
      def.setMembers(List.of());
    }
    if (!def.getStructure().contains(ObservationDefinitionStructure.component)) {
      def.setComponents(List.of());
    }
  }

  private void validate(ObservationDefinition def) {
    if (def.getStructure().contains(ObservationDefinitionStructure.panel) && CollectionUtils.isEmpty(def.getMembers())) {
      throw ApiError.OD000.toApiException();
    }
    if (def.getStructure().contains(ObservationDefinitionStructure.component) && CollectionUtils.isEmpty(def.getComponents())) {
      throw ApiError.OD001.toApiException();
    }
  }
}
