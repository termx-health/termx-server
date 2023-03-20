package com.kodality.termserver.ext.observationdefinition;

import com.kodality.termserver.ts.codesystem.CodeSystemEntityVersion;
import com.kodality.termserver.ts.codesystem.Concept;
import com.kodality.termserver.ts.valueset.concept.ValueSetExternalExpandProvider;
import com.kodality.termserver.ts.valueset.ruleset.ValueSetVersionRuleSetService;
import com.kodality.termserver.ts.valueset.ValueSetVersionConcept;
import com.kodality.termserver.ts.valueset.ValueSetVersionRuleSet.ValueSetVersionRule;
import com.kodality.termserver.ts.valueset.ValueSetVersionRuleSet.ValueSetVersionRule.ValueSetRuleFilter;
import com.kodality.termsupp.observationdefinition.ObservationDefinition;
import com.kodality.termsupp.observationdefinition.ObservationDefinitionService;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Singleton;

@Requires(property = "supplement.observation-definition", value = StringUtils.TRUE)
@Singleton
public class ObservationDefinitionValueSetExpandProvider extends ValueSetExternalExpandProvider {
  private final ObservationDefinitionMapper mapper;
  private final ObservationDefinitionService observationDefinitionService;

  private static final String OBS_DEF = "observation-definition";

  public ObservationDefinitionValueSetExpandProvider(ValueSetVersionRuleSetService valueSetVersionRuleSetService,
                                                     ObservationDefinitionMapper mapper, ObservationDefinitionService observationDefinitionService) {
    super(valueSetVersionRuleSetService);
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
