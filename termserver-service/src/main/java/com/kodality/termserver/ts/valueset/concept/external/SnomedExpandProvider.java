package com.kodality.termserver.ts.valueset.concept.external;

import com.kodality.termserver.codesystem.Designation;
import com.kodality.termserver.integration.snomed.SnomedMapper;
import com.kodality.termserver.integration.snomed.SnomedService;
import com.kodality.termserver.snomed.concept.SnomedConcept;
import com.kodality.termserver.snomed.concept.SnomedConceptSearchParams;
import com.kodality.termserver.ts.valueset.ruleset.ValueSetVersionRuleSetService;
import com.kodality.termserver.valueset.ValueSetVersionConcept;
import com.kodality.termserver.valueset.ValueSetVersionRuleSet;
import com.kodality.termserver.valueset.ValueSetVersionRuleSet.ValueSetVersionRule;
import com.kodality.termserver.valueset.ValueSetVersionRuleSet.ValueSetVersionRule.ValueSetRuleFilter;
import io.micronaut.core.util.CollectionUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class SnomedExpandProvider extends ValueSetExternalExpandProvider {
  private final SnomedMapper snomedMapper;
  private final SnomedService snomedService;
  private final ValueSetVersionRuleSetService valueSetVersionRuleSetService;

  private static final String SNOMED = "snomed-ct";
  private static final String SNOMED_IS_A = "is-a";
  private static final String SNOMED_IN = "in";

  @Override
  public List<ValueSetVersionConcept> expand(Long versionId, ValueSetVersionRuleSet ruleSet) {
    if (versionId != null) {
      ruleSet = valueSetVersionRuleSetService.load(versionId).orElse(ruleSet);
    }
    if (ruleSet == null) {
      return new ArrayList<>();
    }
    List<ValueSetVersionConcept> include = ruleSet.getRules().stream()
        .filter(r -> SNOMED.equals(r.getCodeSystem()) && r.getType().equals("include"))
        .flatMap(rule -> snomedRuleExpand(rule).stream()).toList();
    List<ValueSetVersionConcept> exclude = ruleSet.getRules().stream()
        .filter(r -> SNOMED.equals(r.getCodeSystem()) && r.getType().equals("exclude"))
        .flatMap(rule -> snomedRuleExpand(rule).stream()).toList();
    return include.stream().filter(ic -> exclude.stream().noneMatch(ec -> ec.getConcept().getCode().equals(ic.getConcept().getCode())))
        .collect(Collectors.toList());
  }

  private List<ValueSetVersionConcept> snomedRuleExpand(ValueSetVersionRule rule) {
    List<ValueSetVersionConcept> ruleConcepts = CollectionUtils.isEmpty(rule.getConcepts()) ? new ArrayList<>() : prepare(rule.getConcepts());
    if (CollectionUtils.isEmpty(rule.getFilters())) {
      ruleConcepts.forEach(c -> {
        if (c.getDisplay() == null || c.getDisplay().getName() == null) {
          Optional<SnomedConcept> concept = snomedService.searchConcepts(new SnomedConceptSearchParams().setConceptIds(List.of(c.getConcept().getCode()))).stream().findFirst();
          c.setDisplay(concept.map(sc -> new Designation().setName(sc.getPt().getTerm()).setLanguage(sc.getPt().getLang())).orElse(null));
        }
      });
      return ruleConcepts;
    }
    rule.getFilters().forEach(f -> ruleConcepts.addAll(snomedService.searchConcepts(new SnomedConceptSearchParams().setEcl(composeEcl(f)).setAll(true)).stream()
        .map(c -> new ValueSetVersionConcept()
            .setConcept(snomedMapper.toConcept(c))
            .setActive(c.isActive())
            .setDisplay(new Designation().setName(c.getPt().getTerm()).setLanguage(c.getPt().getLang())))
        .toList()));
    return ruleConcepts;
  }

  private List<ValueSetVersionConcept> prepare(List<ValueSetVersionConcept> concepts) {
    SnomedConceptSearchParams params = new SnomedConceptSearchParams();
    params.setConceptIds(concepts.stream().map(c -> c.getConcept().getCode()).collect(Collectors.toList()));
    params.setActive(true);
    params.setAll(true);
    List<SnomedConcept> snomedConcepts = snomedService.searchConcepts(params);
    concepts.forEach(c -> c.setActive(snomedConcepts.stream().anyMatch(sc -> sc.getConceptId().equals(c.getConcept().getCode()))));
    return concepts;
  }

  private String composeEcl(ValueSetRuleFilter f) {
    String ecl = "";
    if (f.getOperator().equals(SNOMED_IS_A)) {
      ecl += "<<";
    }
    if (f.getOperator().equals(SNOMED_IN)) {
      ecl += "^";
    }
    ecl += f.getValue();
    return ecl;
  }
}
