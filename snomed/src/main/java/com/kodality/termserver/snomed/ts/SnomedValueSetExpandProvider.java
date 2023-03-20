package com.kodality.termserver.snomed.ts;

import com.kodality.termserver.ts.ValueSetExternalExpandProvider;
import com.kodality.termserver.ts.codesystem.Designation;
import com.kodality.termserver.snomed.snomed.SnomedService;
import com.kodality.termserver.snomed.concept.SnomedConcept;
import com.kodality.termserver.snomed.concept.SnomedConceptSearchParams;
import com.kodality.termserver.ts.valueset.ValueSetVersionConcept;
import com.kodality.termserver.ts.valueset.ValueSetVersionRuleSet.ValueSetVersionRule;
import com.kodality.termserver.ts.valueset.ValueSetVersionRuleSet.ValueSetVersionRule.ValueSetRuleFilter;
import io.micronaut.core.util.CollectionUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.inject.Singleton;

@Singleton
public class SnomedValueSetExpandProvider extends ValueSetExternalExpandProvider {
  private final SnomedMapper snomedMapper;
  private final SnomedService snomedService;

  private static final String SNOMED = "snomed-ct";
  private static final String SNOMED_IS_A = "is-a";
  private static final String SNOMED_IN = "in";

  public SnomedValueSetExpandProvider(SnomedMapper snomedMapper, SnomedService snomedService) {
    this.snomedMapper = snomedMapper;
    this.snomedService = snomedService;
  }

  @Override
  public List<ValueSetVersionConcept> ruleExpand(ValueSetVersionRule rule) {
    List<ValueSetVersionConcept> ruleConcepts = CollectionUtils.isEmpty(rule.getConcepts()) ? new ArrayList<>() : prepare(rule.getConcepts());
    if (CollectionUtils.isEmpty(rule.getFilters())) {
      ruleConcepts.forEach(this::decorate);
      return ruleConcepts;
    }
    rule.getFilters().forEach(f -> ruleConcepts.addAll(filterConcepts(f)));
    return ruleConcepts;
  }

  private List<ValueSetVersionConcept> filterConcepts(ValueSetRuleFilter filter) {
    String ecl = composeEcl(filter);
    List<SnomedConcept> snomedConcepts = snomedService.searchConcepts(new SnomedConceptSearchParams().setEcl(ecl).setAll(true));
    return snomedConcepts.stream().map(c -> new ValueSetVersionConcept()
            .setConcept(snomedMapper.toConcept(c))
            .setActive(c.isActive())
            .setDisplay(new Designation().setName(c.getPt().getTerm()).setLanguage(c.getPt().getLang()))).toList();
  }

  private void decorate(ValueSetVersionConcept c) {
    if (c.getDisplay() != null && c.getDisplay().getName() != null) {
      return;
    }
    Optional<SnomedConcept> concept = snomedService.searchConcepts(new SnomedConceptSearchParams().setConceptIds(List.of(c.getConcept().getCode()))).stream().findFirst();
    c.setDisplay(concept.map(sc -> new Designation().setName(sc.getPt().getTerm()).setLanguage(sc.getPt().getLang())).orElse(null));
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

  @Override
  public String getCodeSystemId() {
    return SNOMED;
  }
}
