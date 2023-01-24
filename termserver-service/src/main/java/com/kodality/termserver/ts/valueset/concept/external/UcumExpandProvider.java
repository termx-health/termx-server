package com.kodality.termserver.ts.valueset.concept.external;

import com.kodality.termserver.codesystem.Designation;
import com.kodality.termserver.measurementunit.MeasurementUnit;
import com.kodality.termserver.measurementunit.MeasurementUnitQueryParams;
import com.kodality.termserver.ts.measurementunit.MeasurementUnitService;
import com.kodality.termserver.ts.valueset.ruleset.ValueSetVersionRuleSetService;
import com.kodality.termserver.valueset.ValueSetVersionConcept;
import com.kodality.termserver.valueset.ValueSetVersionRuleSet;
import com.kodality.termserver.valueset.ValueSetVersionRuleSet.ValueSetVersionRule;
import io.micronaut.core.util.CollectionUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class UcumExpandProvider extends ValueSetExternalExpandProvider {
  private final MeasurementUnitService measurementUnitService;
  private final ValueSetVersionRuleSetService valueSetVersionRuleSetService;

  private static final String UCUM = "ucum";

  @Override
  public List<ValueSetVersionConcept> expand(Long versionId, ValueSetVersionRuleSet ruleSet) {
    if (versionId != null) {
      ruleSet = valueSetVersionRuleSetService.load(versionId).orElse(ruleSet);
    }
    if (ruleSet == null) {
      return new ArrayList<>();
    }
    List<ValueSetVersionConcept> include = ruleSet.getRules().stream()
        .filter(r -> UCUM.equals(r.getCodeSystem()) && r.getType().equals("include"))
        .flatMap(rule -> ruleExpand(rule).stream()).toList();
    List<ValueSetVersionConcept> exclude = ruleSet.getRules().stream()
        .filter(r -> UCUM.equals(r.getCodeSystem()) && r.getType().equals("exclude"))
        .flatMap(rule -> ruleExpand(rule).stream()).toList();
    return include.stream().filter(ic -> exclude.stream().noneMatch(ec -> ec.getConcept().getCode().equals(ic.getConcept().getCode())))
        .collect(Collectors.toList());
  }

  private List<ValueSetVersionConcept> ruleExpand(ValueSetVersionRule rule) {
    List<ValueSetVersionConcept> ruleConcepts = CollectionUtils.isEmpty(rule.getConcepts()) ? new ArrayList<>() : rule.getConcepts();
    if (CollectionUtils.isEmpty(rule.getFilters())) {
      ruleConcepts.forEach(c -> {
        MeasurementUnitQueryParams params = new MeasurementUnitQueryParams();
        params.setCode(c.getConcept().getCode());
        params.setLimit(1);
        Optional<MeasurementUnit> concept = measurementUnitService.query(params).findFirst();
        c.setAdditionalDesignations(concept.map(unit -> unit.getNames().entrySet().stream().map(n -> new Designation().setName(n.getValue()).setLanguage(n.getKey())).toList()).orElse(null));
        c.setActive(true);
      });
      return ruleConcepts;
    }
    return ruleConcepts;
  }
}
