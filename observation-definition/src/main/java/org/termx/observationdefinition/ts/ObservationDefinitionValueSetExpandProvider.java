package org.termx.observationdefinition.ts;

import org.termx.core.ts.ValueSetExternalExpandProvider;
import org.termx.observationdefinition.observationdefinition.ObservationDefinitionService;
import org.termx.observationdefintion.ObservationDefinition;
import org.termx.ts.codesystem.CodeSystemEntityVersion;
import org.termx.ts.codesystem.Concept;
import org.termx.ts.valueset.ValueSetVersion;
import org.termx.ts.valueset.ValueSetVersionConcept;
import org.termx.ts.valueset.ValueSetVersionRuleSet.ValueSetVersionRule;
import org.termx.ts.valueset.ValueSetVersionRuleSet.ValueSetVersionRule.ValueSetRuleFilter;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import java.util.ArrayList;
import java.util.List;
import jakarta.inject.Singleton;

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
  public List<ValueSetVersionConcept> ruleExpand(ValueSetVersionRule rule, ValueSetVersion version, String preferredLanguage) {
    List<ValueSetVersionConcept> ruleConcepts = CollectionUtils.isEmpty(rule.getConcepts()) ? new ArrayList<>() : rule.getConcepts();
    if (CollectionUtils.isEmpty(rule.getFilters())) {
      ruleConcepts.forEach(this::decorate);
      return ruleConcepts;
    }
    rule.getFilters().forEach(f -> ruleConcepts.addAll(filterConcepts(f)));
    return ruleConcepts;
  }

  private void decorate(ValueSetVersionConcept c) {
    if (c.getDisplay() != null && StringUtils.isNotEmpty(c.getDisplay().getName())) {
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
