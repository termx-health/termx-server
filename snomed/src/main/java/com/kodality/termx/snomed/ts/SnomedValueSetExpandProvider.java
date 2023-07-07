package com.kodality.termx.snomed.ts;

import com.kodality.termx.ts.Language;
import com.kodality.termx.ts.ValueSetExternalExpandProvider;
import com.kodality.termx.ts.codesystem.Designation;
import com.kodality.termx.snomed.snomed.SnomedService;
import com.kodality.termx.snomed.concept.SnomedConcept;
import com.kodality.termx.snomed.concept.SnomedConceptSearchParams;
import com.kodality.termx.ts.valueset.ValueSetVersion;
import com.kodality.termx.ts.valueset.ValueSetVersionConcept;
import com.kodality.termx.ts.valueset.ValueSetVersionRuleSet.ValueSetVersionRule;
import com.kodality.termx.ts.valueset.ValueSetVersionRuleSet.ValueSetVersionRule.ValueSetRuleFilter;
import io.micronaut.core.util.CollectionUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
  public List<ValueSetVersionConcept> ruleExpand(ValueSetVersionRule rule, ValueSetVersion version) {
    List<String> languages = CollectionUtils.isEmpty(version.getSupportedLanguages()) ? List.of(Language.en) : version.getSupportedLanguages();
    if (CollectionUtils.isNotEmpty(rule.getConcepts())) {
      return prepare(rule.getConcepts(), languages);
    }
    if (CollectionUtils.isNotEmpty(rule.getFilters())) {
      return rule.getFilters().stream().flatMap(f -> filterConcepts(f).stream()).collect(Collectors.toList());
    }
    return new ArrayList<>();
  }

  private List<ValueSetVersionConcept> filterConcepts(ValueSetRuleFilter filter) {
    String ecl = composeEcl(filter);
    List<SnomedConcept> snomedConcepts = snomedService.searchConcepts(new SnomedConceptSearchParams().setEcl(ecl).setAll(true));
    return snomedConcepts.stream().map(c -> new ValueSetVersionConcept()
        .setConcept(snomedMapper.toConcept(c))
        .setActive(c.isActive())
        .setDisplay(new Designation().setName(c.getPt().getTerm()).setLanguage(c.getPt().getLang()))).toList();
  }

  private List<ValueSetVersionConcept> prepare(List<ValueSetVersionConcept> concepts, List<String> languages) {
    SnomedConceptSearchParams params = new SnomedConceptSearchParams();
    params.setConceptIds(concepts.stream().map(c -> c.getConcept().getCode()).collect(Collectors.toList()));
    params.setAll(true);
    Map<String, SnomedConcept> snomedConcepts = snomedService.searchConcepts(params).stream().collect(Collectors.toMap(SnomedConcept::getConceptId, c -> c));
    concepts.forEach(c -> {
      SnomedConcept sc = snomedConcepts.get(c.getConcept().getCode());
      if (sc == null) {
        return;
      }
      c.setActive(sc.isActive());
      c.setDisplay(new Designation().setName(sc.getPt().getTerm()).setLanguage(sc.getPt().getLang()));
      c.setAdditionalDesignations(sc.getDescriptions().stream().filter(d -> languages.contains(d.getLang()))
          .map(d -> new Designation().setName(d.getTerm()).setLanguage(d.getLang())).toList());
    });
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
