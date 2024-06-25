package com.kodality.termx.snomed.ts;

import com.kodality.termx.snomed.concept.SnomedConcept;
import com.kodality.termx.snomed.concept.SnomedConceptSearchParams;
import com.kodality.termx.snomed.description.SnomedDescription;
import com.kodality.termx.snomed.snomed.SnomedService;
import com.kodality.termx.core.ts.ValueSetExternalExpandProvider;
import com.kodality.termx.ts.codesystem.Designation;
import com.kodality.termx.ts.valueset.ValueSetVersion;
import com.kodality.termx.ts.valueset.ValueSetVersionConcept;
import com.kodality.termx.ts.valueset.ValueSetVersionRuleSet.ValueSetVersionRule;
import com.kodality.termx.ts.valueset.ValueSetVersionRuleSet.ValueSetVersionRule.ValueSetRuleFilter;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import jakarta.inject.Singleton;


@Singleton
public class SnomedValueSetExpandProvider extends ValueSetExternalExpandProvider {
  private final SnomedMapper snomedMapper;
  private final SnomedService snomedService;

  private static final String SNOMED = "snomed-ct";
  private static final String SNOMED_IS_A = "is-a";
  private static final String SNOMED_DESCENDENT_OF = "descendent-of";
  private static final String SNOMED_IN = "in";

  public SnomedValueSetExpandProvider(SnomedMapper snomedMapper, SnomedService snomedService) {
    this.snomedMapper = snomedMapper;
    this.snomedService = snomedService;
  }

  @Override
  public List<ValueSetVersionConcept> ruleExpand(ValueSetVersionRule rule, ValueSetVersion version, String preferredLanguage) {
    List<String> supportedLanguages = Optional.ofNullable(version.getSupportedLanguages()).orElse(List.of());
    List<String> preferredLanguages = preferredLanguage != null ? List.of(preferredLanguage) :
        version.getPreferredLanguage() != null ? List.of(version.getPreferredLanguage()) : List.of();

    if (CollectionUtils.isNotEmpty(rule.getConcepts())) {
      return prepare(rule.getConcepts(), preferredLanguages, supportedLanguages);
    }
    if (CollectionUtils.isNotEmpty(rule.getFilters())) {
      return rule.getFilters().stream().flatMap(f -> filterConcepts(f, preferredLanguages, supportedLanguages).stream()).collect(Collectors.toList());
    }
    return new ArrayList<>();
  }

  private List<ValueSetVersionConcept> filterConcepts(ValueSetRuleFilter filter, List<String> preferredLanguages, List<String> supportedLanguages) {
    String ecl = composeEcl(filter);
    List<SnomedConcept> snomedConcepts = snomedService.searchConcepts(new SnomedConceptSearchParams().setEcl(ecl).setAll(true));

    Map<String, List<SnomedDescription>> snomedDescriptions = getDescriptions(snomedConcepts.stream().map(SnomedConcept::getConceptId).toList());

    return snomedConcepts.stream().map(sc -> {
          ValueSetVersionConcept c = new ValueSetVersionConcept();
          c.setConcept(snomedMapper.toVSConcept(sc));
          c.setActive(sc.isActive());
          c.setDisplay(findDisplay(snomedDescriptions.get(sc.getConceptId()), preferredLanguages));
          c.setAdditionalDesignations(
              findDesignations(snomedDescriptions.get(sc.getConceptId()), supportedLanguages, c.getDisplay() != null ? c.getDisplay().getDesignationType() : null));
          return c;
        }
    ).toList();
  }

  private List<ValueSetVersionConcept> prepare(List<ValueSetVersionConcept> concepts, List<String> preferredLanguages, List<String> supportedLanguages) {
    Map<String, List<SnomedConcept>> snomedConcepts =
        snomedService.loadConcepts(concepts.stream().map(c -> c.getConcept().getCode()).collect(Collectors.toList())).stream()
            .collect(Collectors.groupingBy(SnomedConcept::getConceptId));

    Map<String, List<SnomedDescription>> snomedDescriptions = getDescriptions(snomedConcepts.keySet().stream().toList());

    concepts.forEach(c -> {
      SnomedConcept sc = Optional.ofNullable(snomedConcepts.get(c.getConcept().getCode())).flatMap(l -> l.stream().findFirst()).orElse(null);
      if (sc == null) {
        return;
      }
      c.setConcept(snomedMapper.toVSConcept(sc));
      c.setActive(sc.isActive());
      c.setDisplay(c.getDisplay() == null || StringUtils.isEmpty(c.getDisplay().getName()) ? findDisplay(snomedDescriptions.get(sc.getConceptId()), preferredLanguages) : c.getDisplay());
      c.setAdditionalDesignations(CollectionUtils.isNotEmpty(c.getAdditionalDesignations()) ? c.getAdditionalDesignations() :
          findDesignations(snomedDescriptions.get(sc.getConceptId()), supportedLanguages, c.getDisplay() != null ? c.getDisplay().getDesignationType() : null));
    });
    return concepts.stream().sorted(Comparator.comparing(c -> c.getOrderNumber() == null ? 0 : c.getOrderNumber())).toList();
  }

  private Map<String, List<SnomedDescription>> getDescriptions(List<String> conceptIds) {
    if (CollectionUtils.isEmpty(conceptIds)) {
      return Map.of();
    }
    return snomedService.loadDescriptions(conceptIds).stream().collect(Collectors.groupingBy(SnomedDescription::getConceptId));
  }

  private Designation findDisplay(List<SnomedDescription> snomedDescriptions, List<String> preferredLanguages) {
    SnomedDescription description = snomedDescriptions.stream()
        .filter(d -> CollectionUtils.isEmpty(preferredLanguages) || d.getLang() != null && preferredLanguages.contains(d.getLang()))
        .sorted((d1, d2) -> Boolean.compare(!d1.getTypeId().equals("900000000000013009"), !d2.getTypeId().equals("900000000000013009")))
        .max((d1, d2) -> Boolean.compare(d1.getAcceptabilityMap().containsValue("PREFERRED"), d2.getAcceptabilityMap().containsValue("PREFERRED"))).orElse(null);
    if (description == null) {
      return null;
    }
    return new Designation().setName(description.getTerm()).setLanguage(description.getLang()).setDesignationType(description.getDescriptionId());
  }

  private List<Designation> findDesignations(List<SnomedDescription> snomedDescriptions, List<String> supportedLanguages, String displayId) {
    return snomedDescriptions.stream()
        .filter(d -> CollectionUtils.isEmpty(supportedLanguages) || d.getLang() != null && supportedLanguages.contains(d.getLang()))
        .filter(d -> !d.getDescriptionId().equals(displayId))
        .map(d -> new Designation().setName(d.getTerm()).setLanguage(d.getLang())).toList();
  }

  private String composeEcl(ValueSetRuleFilter f) {
    String ecl = "";
    if (f.getOperator().equals(SNOMED_IS_A)) {
      ecl += "<<";
    }
    if (f.getOperator().equals(SNOMED_DESCENDENT_OF)) {
      ecl += "<";
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
