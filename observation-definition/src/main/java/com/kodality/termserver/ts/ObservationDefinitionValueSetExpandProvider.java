package com.kodality.termserver.ts;

import com.kodality.termserver.observationdefinition.ObservationDefinitionService;
import com.kodality.termserver.observationdefintion.ObservationDefinition;
import com.kodality.termserver.ts.codesystem.CodeSystemEntityVersion;
import com.kodality.termserver.ts.codesystem.Concept;
import com.kodality.termserver.ts.valueset.ValueSetVersionConcept;
import com.kodality.termserver.ts.valueset.ValueSetVersionRuleSet.ValueSetVersionRule;
import com.kodality.termserver.ts.valueset.ValueSetVersionRuleSet.ValueSetVersionRule.ValueSetRuleFilter;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Singleton;

@Requires(value = StringUtils.TRUE)
@Singleton
public class ObservationDefinitionValueSetExpandProvider extends ValueSetExternalExpandProvider {
  private final ObservationDefinitionMapper mapper;
  private final ObservationDefinitionService observationDefinitionService;

  private static final String OBS_DEF = "observation-definition";

  public ObservationDefinitionValueSetExpandProvider(ObservationDefinitionMapper mapper, ObservationDefinitionService observationDefinitionService) {
    this.mapper = mapper;
    this.observationDefinitionService = observationDefinitionService;
  }

  @Override
  public List<ValueSetVersionConcept> ruleExpand(ValueSetVersionRule rule) {
    List<ValueSetVersionConcept> ruleConcepts = CollectionUtils.isEmpty(rule.getConcepts()) ? new ArrayList<>() : rule.getConcepts();
    if (CollectionUtils.isEmpty(rule.getFilters())) {
      ruleConcepts.forEach(this::decorate);
      return ruleConcepts;
    }
    rule.getFilters().forEach(f -> ruleConcepts.addAll(filterConcepts(f)));
    return ruleConcepts;
  }

  private void decorate(ValueSetVersionConcept c) {
    if (c.getDisplay() != null && c.getDisplay().getName() != null) {
      return;
    }
    ObservationDefinition observationDefinition = observationDefinitionService.load(c.getConcept().getCode());
    Concept concept = mapper.toConcept(observationDefinition);
    c.setAdditionalDesignations(c.getAdditionalDesignations() == null ? new ArrayList<>() : c.getAdditionalDesignations());
    c.getAdditionalDesignations().addAll(concept.getVersions().stream().findFirst().map(CodeSystemEntityVersion::getDesignations).orElse(new ArrayList<>()));
  }

  private List<ValueSetVersionConcept> filterConcepts(ValueSetRuleFilter filter) {
    //TODO
    return new ArrayList<>();
  }

  @Override
  public String getCodeSystemId() {
    return OBS_DEF;
  }
}
