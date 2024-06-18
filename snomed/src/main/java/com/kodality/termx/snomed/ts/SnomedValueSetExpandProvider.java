package com.kodality.termx.snomed.ts;

import com.kodality.termx.core.ts.CodeSystemProvider;
import com.kodality.termx.core.ts.ValueSetExternalExpandProvider;
import com.kodality.termx.snomed.concept.SnomedConcept;
import com.kodality.termx.snomed.concept.SnomedConceptSearchParams;
import com.kodality.termx.snomed.description.SnomedDescription;
import com.kodality.termx.snomed.snomed.SnomedService;
import com.kodality.termx.ts.codesystem.CodeSystemVersionReference;
import com.kodality.termx.ts.codesystem.ConceptQueryParams;
import com.kodality.termx.ts.codesystem.Designation;
import com.kodality.termx.ts.valueset.ValueSetVersion;
import com.kodality.termx.ts.valueset.ValueSetVersionConcept;
import com.kodality.termx.ts.valueset.ValueSetVersionRuleSet.ValueSetVersionRule;
import com.kodality.termx.ts.valueset.ValueSetVersionRuleSet.ValueSetVersionRule.ValueSetRuleFilter;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.inject.Singleton;


@Singleton
public class SnomedValueSetExpandProvider extends ValueSetExternalExpandProvider {
  private final SnomedMapper snomedMapper;
  private final SnomedService snomedService;
  private final CodeSystemProvider codeSystemProvider;

  private static final String SNOMED = "snomed-ct";
  private static final String SNOMED_IS_A = "is-a";
  private static final String SNOMED_DESCENDENT_OF = "descendent-of";
  private static final String SNOMED_IN = "in";

  public SnomedValueSetExpandProvider(SnomedMapper snomedMapper, SnomedService snomedService, CodeSystemProvider codeSystemProvider) {
    this.snomedMapper = snomedMapper;
    this.snomedService = snomedService;
    this.codeSystemProvider = codeSystemProvider;
  }

  @Override
  public List<ValueSetVersionConcept> ruleExpand(ValueSetVersionRule rule, ValueSetVersion version, String preferredLanguage) {
    List<String> supportedLanguages = Optional.ofNullable(version.getSupportedLanguages()).orElse(List.of());
    List<String> preferredLanguages = preferredLanguage != null ? List.of(preferredLanguage) :
        version.getPreferredLanguage() != null ? List.of(version.getPreferredLanguage()) : List.of();

    String branch = getBranch(rule.getCodeSystemVersion());
    List<ValueSetVersionConcept> concepts = new ArrayList<>();
    if (CollectionUtils.isNotEmpty(rule.getConcepts())) {
      concepts.addAll(prepare(rule.getConcepts(), preferredLanguages, supportedLanguages, branch));
    }
    if (CollectionUtils.isNotEmpty(rule.getFilters())) {
      concepts.addAll(rule.getFilters().stream().flatMap(f -> filterConcepts(f, preferredLanguages, supportedLanguages, branch).stream()).toList());
    }
    if (rule.getCodeSystemVersion() != null && rule.getCodeSystemVersion().getVersion() != null) {
      concepts.forEach(c -> c.getConcept().setCodeSystemVersions(List.of(rule.getCodeSystemVersion().getVersion())));
    }
    return concepts;
  }

  private List<ValueSetVersionConcept> filterConcepts(ValueSetRuleFilter filter, List<String> preferredLanguages, List<String> supportedLanguages, String branch) {
    String ecl = composeEcl(filter);
    List<SnomedConcept> snomedConcepts = snomedService.searchConcepts(new SnomedConceptSearchParams().setEcl(ecl).setBranch(branch).setAll(true));

    Map<String, List<SnomedDescription>> snomedDescriptions = getDescriptions(snomedConcepts.stream().map(SnomedConcept::getConceptId).toList(), branch);

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

  private List<ValueSetVersionConcept> prepare(List<ValueSetVersionConcept> concepts, List<String> preferredLanguages, List<String> supportedLanguages, String branch) {
    Map<String, List<SnomedConcept>> snomedConcepts =
        snomedService.loadConcepts(concepts.stream().map(c -> c.getConcept().getCode()).collect(Collectors.toList()), branch).stream()
            .collect(Collectors.groupingBy(SnomedConcept::getConceptId));

    Map<String, List<SnomedDescription>> snomedDescriptions = getDescriptions(snomedConcepts.keySet().stream().toList(), branch);

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

  private Map<String, List<SnomedDescription>> getDescriptions(List<String> conceptIds, String branch) {
    if (CollectionUtils.isEmpty(conceptIds)) {
      return Map.of();
    }
    return snomedService.loadDescriptions(branch, conceptIds).stream().collect(Collectors.groupingBy(SnomedDescription::getConceptId));
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
        .filter(SnomedDescription::isActive)
        .map(snomedMapper::toConceptDesignation).toList();
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

  private String getBranch(CodeSystemVersionReference codeSystemVersion) {
    if (codeSystemVersion == null || codeSystemVersion.getUri() == null) {
      return null;
    }

    String[] uri = codeSystemVersion.getUri().split("/");
    if (uri.length < 5) {
      return null;
    }

    String moduleId = uri[4];
    Map<String, String> modules = loadModules();
    String branchPath = modules.get(moduleId);
    if (branchPath == null) {
      return null;
    }

    String version = uri.length < 7 ? null : getBranchVersion(uri[6]);
    return branchPath + (version == null ? "" : "/" + version);
  }

  private String getBranchVersion(String yyyyMMdd) {
    DateTimeFormatter inputFormatter = DateTimeFormatter.ofPattern("yyyyMMdd");
    DateTimeFormatter outputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    try {
      LocalDate date = LocalDate.parse(yyyyMMdd, inputFormatter);
      return date.format(outputFormatter);
    } catch (DateTimeParseException e) {
      return null;
    }
  }

  private Map<String, String> loadModules() {
    ConceptQueryParams params = new ConceptQueryParams().setCodeSystem("snomed-module").limit(-1);
    return codeSystemProvider.searchConcepts(params).getData().stream()
        .filter(c -> c.getLastVersion().map(v -> v.getPropertyValue("moduleId").isPresent() && v.getPropertyValue("branchPath").isPresent()).orElse(false))
        .collect(Collectors.toMap(c -> (String) c.getLastVersion().get().getPropertyValue("moduleId").get(), c -> (String) c.getLastVersion().get().getPropertyValue("branchPath").get()));
  }


  @Override
  public String getCodeSystemId() {
    return SNOMED;
  }
}
