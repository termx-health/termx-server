package com.kodality.termx.ucum.ts;

import com.kodality.termx.core.ts.ValueSetExternalExpandProvider;
import com.kodality.termx.ts.valueset.ValueSetVersion;
import com.kodality.termx.ts.valueset.ValueSetVersionConcept;
import com.kodality.termx.ts.valueset.ValueSetVersionRuleSet.ValueSetVersionRule;
import com.kodality.termx.ts.valueset.ValueSetVersionRuleSet.ValueSetVersionRule.ValueSetRuleFilter;
import io.micronaut.core.util.CollectionUtils;
import java.util.ArrayList;
import java.util.List;
import jakarta.inject.Singleton;

@Singleton
public class UcumValueSetExpandProvider extends ValueSetExternalExpandProvider {
  private final UcumConceptResolver ucumConceptResolver;

  private static final String UCUM = "ucum";
  private static final String UCUM_KIND = "kind";

  public UcumValueSetExpandProvider(UcumConceptResolver ucumConceptResolver) {
    this.ucumConceptResolver = ucumConceptResolver;
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

  private List<ValueSetVersionConcept> filterConcepts(ValueSetRuleFilter filter) {
    if (UCUM_KIND.equals(filter.getProperty().getName())) {
      return ucumConceptResolver.expandByKind(filter.getValue());
    } else {
      return new ArrayList<>();
    }
  }

  private void decorate(ValueSetVersionConcept c) {
    ucumConceptResolver.decorate(c);
  }

  @Override
  public String getCodeSystemId() {
    return UCUM;
  }
}
