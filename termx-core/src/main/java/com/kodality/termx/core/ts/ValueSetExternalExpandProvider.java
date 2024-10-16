package com.kodality.termx.core.ts;

import com.kodality.termx.ts.valueset.ValueSetVersion;
import com.kodality.termx.ts.valueset.ValueSetVersionConcept;
import com.kodality.termx.ts.valueset.ValueSetVersionRuleSet;
import com.kodality.termx.ts.valueset.ValueSetVersionRuleSet.ValueSetVersionRule;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public abstract class ValueSetExternalExpandProvider {
  public List<ValueSetVersionConcept> expand(ValueSetVersionRuleSet ruleSet, ValueSetVersion version, String preferredLanguage) {
    if (ruleSet == null || ruleSet.getRules() == null) {
      return new ArrayList<>();
    }
    List<ValueSetVersionConcept> include = ruleSet.getRules().stream()
        .filter(r -> getCodeSystemId().equals(r.getCodeSystem()) && r.getType().equals("include"))
        .flatMap(rule -> ruleExpand(rule, version, preferredLanguage).stream()).toList();
    List<ValueSetVersionConcept> exclude = ruleSet.getRules().stream()
        .filter(r -> getCodeSystemId().equals(r.getCodeSystem()) && r.getType().equals("exclude"))
        .flatMap(rule -> ruleExpand(rule, version, preferredLanguage).stream()).toList();
    return include.stream().filter(ic -> exclude.stream().noneMatch(ec -> ec.getConcept().getCode().equals(ic.getConcept().getCode())))
        .collect(Collectors.toList());
  }

  public abstract List<ValueSetVersionConcept> ruleExpand(ValueSetVersionRule rule, ValueSetVersion version, String preferredLanguage);

  public abstract String getCodeSystemId();
}
