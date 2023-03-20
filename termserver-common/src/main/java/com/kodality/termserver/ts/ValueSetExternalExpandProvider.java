package com.kodality.termserver.ts;

import com.kodality.termserver.ts.valueset.ValueSetVersionConcept;
import com.kodality.termserver.ts.valueset.ValueSetVersionRuleSet;
import com.kodality.termserver.ts.valueset.ValueSetVersionRuleSet.ValueSetVersionRule;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public abstract class ValueSetExternalExpandProvider {
  public List<ValueSetVersionConcept> expand(ValueSetVersionRuleSet ruleSet) {
    if (ruleSet == null) {
      return new ArrayList<>();
    }
    List<ValueSetVersionConcept> include = ruleSet.getRules().stream()
        .filter(r -> getCodeSystemId().equals(r.getCodeSystem()) && r.getType().equals("include"))
        .flatMap(rule -> ruleExpand(rule).stream()).toList();
    List<ValueSetVersionConcept> exclude = ruleSet.getRules().stream()
        .filter(r -> getCodeSystemId().equals(r.getCodeSystem()) && r.getType().equals("exclude"))
        .flatMap(rule -> ruleExpand(rule).stream()).toList();
    return include.stream().filter(ic -> exclude.stream().noneMatch(ec -> ec.getConcept().getCode().equals(ic.getConcept().getCode())))
        .collect(Collectors.toList());
  }

  public abstract List<ValueSetVersionConcept> ruleExpand(ValueSetVersionRule rule);

  public abstract String getCodeSystemId();
}
