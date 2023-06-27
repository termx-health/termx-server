package com.kodality.termserver.snomed.ts;

import com.kodality.termserver.snomed.client.SnowstormClient;
import com.kodality.termserver.ts.Language;
import com.kodality.termserver.ts.ValueSetExternalExpandProvider;
import com.kodality.termserver.ts.codesystem.Designation;
import com.kodality.termserver.snomed.snomed.SnomedService;
import com.kodality.termserver.snomed.concept.SnomedConcept;
import com.kodality.termserver.snomed.concept.SnomedConceptSearchParams;
import com.kodality.termserver.ts.valueset.ValueSetVersion;
import com.kodality.termserver.ts.valueset.ValueSetVersionConcept;
import com.kodality.termserver.ts.valueset.ValueSetVersionRuleSet.ValueSetVersionRule;
import com.kodality.termserver.ts.valueset.ValueSetVersionRuleSet.ValueSetVersionRule.ValueSetRuleFilter;
import io.micronaut.core.util.CollectionUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Singleton;

@Singleton
public class SnomedValueSetExpandProvider extends ValueSetExternalExpandProvider {
  private final SnomedMapper snomedMapper;
  private final SnomedService snomedService;
  private final SnowstormClient snowstormClient;

  private static final String SNOMED = "snomed-ct";
  private static final String SNOMED_IS_A = "is-a";
  private static final String SNOMED_IN = "in";

  public SnomedValueSetExpandProvider(SnomedMapper snomedMapper, SnomedService snomedService, SnowstormClient snowstormClient) {
    this.snomedMapper = snomedMapper;
    this.snomedService = snomedService;
    this.snowstormClient = snowstormClient;
  }

  @Override
  public List<ValueSetVersionConcept> ruleExpand(ValueSetVersionRule rule, ValueSetVersion version) {
    List<ValueSetVersionConcept> ruleConcepts = CollectionUtils.isEmpty(rule.getConcepts()) ? new ArrayList<>() : prepare(rule.getConcepts());
    if (CollectionUtils.isEmpty(rule.getFilters())) {
      ruleConcepts.forEach(c -> decorate(c, CollectionUtils.isEmpty(version.getSupportedLanguages()) ? List.of(Language.en) : version.getSupportedLanguages()));
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

  private void decorate(ValueSetVersionConcept c, List<String> languages) {
    if (c.getDisplay() != null && c.getDisplay().getName() != null) {
      return;
    }
    SnomedConcept sc = snowstormClient.loadConcept(c.getConcept().getCode()).join();
    c.setDisplay(new Designation().setName(sc.getPt().getTerm()).setLanguage(sc.getPt().getLang()));
    c.setAdditionalDesignations(sc.getDescriptions().stream().filter(d -> languages.contains(d.getLang()))
        .map(d -> new Designation().setName(d.getTerm()).setLanguage(d.getLang())).toList());
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
