package com.kodality.termx.snomed.ts;

import com.kodality.termx.snomed.description.SnomedDescription;
import com.kodality.termx.snomed.description.SnomedDescriptionSearchParams;
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
import com.kodality.termx.wiki.PageProvider;
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
      return rule.getFilters().stream().flatMap(f -> filterConcepts(f, languages).stream()).collect(Collectors.toList());
    }
    return new ArrayList<>();
  }

  private List<ValueSetVersionConcept> filterConcepts(ValueSetRuleFilter filter, List<String> languages) {
    String ecl = composeEcl(filter);
    List<SnomedConcept> snomedConcepts = snomedService.searchConcepts(new SnomedConceptSearchParams().setEcl(ecl).setAll(true));

    Map<String, List<SnomedDescription>> snomedDescriptions = getDescriptions(snomedConcepts.stream().map(SnomedConcept::getConceptId).toList());

    return snomedConcepts.stream().map(c -> new ValueSetVersionConcept()
        .setConcept(snomedMapper.toConcept(c))
        .setActive(c.isActive())
        .setDisplay(new Designation().setName(c.getPt().getTerm()).setLanguage(c.getPt().getLang()))
        .setAdditionalDesignations(snomedDescriptions.getOrDefault(c.getConceptId(), List.of()).stream().filter(d -> languages.contains(d.getLang()))
            .map(d -> new Designation().setName(d.getTerm()).setLanguage(d.getLang())).toList())
    ).toList();
  }

  private List<ValueSetVersionConcept> prepare(List<ValueSetVersionConcept> concepts, List<String> languages) {
    SnomedConceptSearchParams params = new SnomedConceptSearchParams();
    params.setConceptIds(concepts.stream().map(c -> c.getConcept().getCode()).collect(Collectors.toList()));
    params.setAll(true);
    Map<String, SnomedConcept> snomedConcepts = snomedService.searchConcepts(params).stream().collect(Collectors.toMap(SnomedConcept::getConceptId, c -> c));

    Map<String, List<SnomedDescription>> snomedDescriptions = getDescriptions(snomedConcepts.keySet().stream().toList());

    concepts.forEach(c -> {
      SnomedConcept sc = snomedConcepts.get(c.getConcept().getCode());
      if (sc == null) {
        return;
      }
      c.setActive(sc.isActive());
      c.setDisplay(new Designation().setName(sc.getPt().getTerm()).setLanguage(sc.getPt().getLang()));
      c.setAdditionalDesignations(snomedDescriptions.getOrDefault(sc.getConceptId(), List.of()).stream().filter(d -> languages.contains(d.getLang()))
          .map(d -> new Designation().setName(d.getTerm()).setLanguage(d.getLang())).toList());
    });
    return concepts;
  }

  private Map<String, List<SnomedDescription>> getDescriptions(List<String> conceptIds) {
    if (CollectionUtils.isEmpty(conceptIds)) {
      return Map.of();
    }
    SnomedDescriptionSearchParams descriptionParams = new SnomedDescriptionSearchParams();
    descriptionParams.setConceptIds(conceptIds);
    descriptionParams.setAll(true);
   return snomedService.searchDescriptions(descriptionParams).stream().collect(Collectors.groupingBy(SnomedDescription::getConceptId));
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
