package org.termx.snomed.ts;

import com.kodality.commons.model.QueryResult;
import org.termx.snomed.ApiError;
import org.termx.core.ts.CodeSystemProvider;
import org.termx.core.ts.ValueSetExternalExpandProvider;
import org.termx.snomed.concept.SnomedConcept;
import org.termx.snomed.concept.SnomedConceptSearchParams;
import org.termx.snomed.description.SnomedDescription;
import org.termx.snomed.integration.SnomedService;
import org.termx.snomed.search.SnomedSearchResult;
import org.termx.ts.codesystem.CodeSystemVersionReference;
import org.termx.ts.codesystem.ConceptQueryParams;
import org.termx.ts.codesystem.Designation;
import org.termx.ts.valueset.ValueSetVersion;
import org.termx.ts.valueset.ValueSetVersionConcept;
import org.termx.ts.valueset.ValueSetVersionRuleSet.ValueSetVersionRule;
import org.termx.ts.valueset.ValueSetVersionRuleSet.ValueSetVersionRule.ValueSetRuleFilter;
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
import jakarta.inject.Singleton;


@Singleton
public class SnomedValueSetExpandProvider extends ValueSetExternalExpandProvider {
  private final SnomedMapper snomedMapper;
  private final SnomedService snomedService;
  private final CodeSystemProvider codeSystemProvider;

  private static final String SNOMED = "snomed-ct";
  private static final String SNOMED_IS_A = "is-a";
  private static final String SNOMED_DESCENDENT_OF = "descendent-of";
  private static final String SNOMED_CHILD_OF = "child-of";
  private static final String SNOMED_GENERALIZES = "generalizes";
  private static final String SNOMED_IN = "in";
  // Synthetic operator produced by the SNOMED implicit-VS routing in ValueSetExpandOperation
  // for `?fhir_vs` (value="*", all concepts) and `?fhir_vs=ecl/<expr>` (value=the raw ECL).
  // It is not a real FHIR filter operator; the value is already a complete ECL expression.
  private static final String SNOMED_ECL = "ecl";

  // Snowstorm caps single-page `limit` at 10_000 (Elasticsearch from+size).
  // A null `count` from the caller means "no client-side cap"; honour Snowstorm's
  // ceiling rather than throwing.
  private static final int SNOWSTORM_MAX_PAGE = 9999;

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

  /**
   * Single-page expansion that pushes paging and the free-text typeahead filter
   * down to Snowstorm — issues exactly one {@code concepts?ecl=…&limit=…&offset=…&term=…}
   * call and loads descriptions only for the returned page. Compare with
   * {@link #ruleExpand} which uses {@code SnomedService.searchConcepts(setAll(true))},
   * looping Snowstorm at limit=9999 until the full ECL result is materialised in
   * heap — fine for narrow filters (a refset of 100 codes), an OOM for
   * {@code ECL=*} against a SNOMED edition.
   *
   * <p>Only the "single filter, no concept list" shape is paged here — that's the
   * shape the SNOMED implicit-VS ({@code ?fhir_vs[=…]}) operation builds.
   * Anything richer falls back to the default {@link #ruleExpandPaged}, which
   * goes through {@link #ruleExpand} and slices in memory.
   *
   * <p>{@code QueryResult.meta.total} is the post-ECL total from Snowstorm's
   * response, so FHIR {@code expansion.total} is correct without a second probe.
   */
  @Override
  public QueryResult<ValueSetVersionConcept> ruleExpandPaged(ValueSetVersionRule rule, ValueSetVersion version,
                                                             String preferredLanguage, String textFilter,
                                                             Integer offset, Integer count) {
    if (rule == null
        || CollectionUtils.isNotEmpty(rule.getConcepts())
        || rule.getFilters() == null
        || rule.getFilters().size() != 1) {
      return super.ruleExpandPaged(rule, version, preferredLanguage, textFilter, offset, count);
    }

    List<String> supportedLanguages = Optional.ofNullable(version.getSupportedLanguages()).orElse(List.of());
    List<String> preferredLanguages = preferredLanguage != null ? List.of(preferredLanguage) :
        version.getPreferredLanguage() != null ? List.of(version.getPreferredLanguage()) : List.of();

    String branch = getBranch(rule.getCodeSystemVersion());
    String ecl = composeEcl(rule.getFilters().get(0));

    int pageOffset = offset == null ? 0 : Math.max(0, offset);
    int pageLimit = count == null ? SNOWSTORM_MAX_PAGE : Math.min(Math.max(0, count), SNOWSTORM_MAX_PAGE);

    SnomedConceptSearchParams params = new SnomedConceptSearchParams()
        .setEcl(ecl)
        .setBranch(branch);
    params.setLimit(pageLimit);
    params.setOffset(pageOffset);
    if (textFilter != null && !textFilter.isBlank()) {
      params.setTerm(textFilter);
    }

    SnomedSearchResult<SnomedConcept> page = snomedService.searchConceptsPage(params);
    List<SnomedConcept> snomedConcepts = page.getItems() != null ? page.getItems() : List.of();
    Integer total = page.getTotal();

    Map<String, List<SnomedDescription>> snomedDescriptions = getDescriptions(
        snomedConcepts.stream().map(SnomedConcept::getConceptId).toList(), branch);

    List<ValueSetVersionConcept> concepts = snomedConcepts.stream().map(sc -> {
      ValueSetVersionConcept c = new ValueSetVersionConcept();
      c.setConcept(snomedMapper.toVSConcept(sc));
      c.setActive(sc.isActive());
      c.setDisplay(findDisplay(snomedDescriptions.get(sc.getConceptId()), preferredLanguages));
      c.setAdditionalDesignations(
          findDesignations(snomedDescriptions.get(sc.getConceptId()), supportedLanguages,
              c.getDisplay() != null ? c.getDisplay().getDesignationType() : null));
      return c;
    }).toList();

    if (rule.getCodeSystemVersion() != null && rule.getCodeSystemVersion().getVersion() != null) {
      concepts.forEach(c -> c.getConcept().setCodeSystemVersions(List.of(rule.getCodeSystemVersion().getVersion())));
    }

    QueryResult<ValueSetVersionConcept> result = new QueryResult<>(concepts);
    result.getMeta().setTotal(total != null ? total : concepts.size());
    result.getMeta().setOffset(pageOffset);
    result.getMeta().setItemsPerPage(pageLimit);
    return result;
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
        snomedService.loadConcepts(concepts.stream().map(c -> c.getConcept().getCode()).toList(), branch).stream()
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
    if (snomedDescriptions == null) {
      return null;
    }
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
    if (snomedDescriptions == null) {
      return List.of();
    }
    return snomedDescriptions.stream()
        .filter(d -> CollectionUtils.isEmpty(supportedLanguages) || d.getLang() != null && supportedLanguages.contains(d.getLang()))
        .filter(d -> !d.getDescriptionId().equals(displayId))
        .filter(SnomedDescription::isActive)
        .map(snomedMapper::toConceptDesignation).toList();
  }

  private String composeEcl(ValueSetRuleFilter f) {
    String operator = f.getOperator() == null ? "" : f.getOperator();
    // The `ecl` operator carries a complete ECL expression already (`*` or a raw ECL); pass it
    // through verbatim — no constraint prefix.
    if (SNOMED_ECL.equals(operator)) {
      return String.valueOf(f.getValue());
    }
    // Map the FHIR filter operator to its ECL constraint operator. Operators with no ECL
    // equivalent (is-not-a, descendent-leaf, regex, =, in-on-property, not-in, exists) are rejected
    // rather than silently emitting a bare, unconstrained value.
    String prefix = switch (operator) {
      case SNOMED_IS_A -> "<<";          // descendant-or-self
      case SNOMED_DESCENDENT_OF -> "<";  // descendants
      case SNOMED_CHILD_OF -> "<!";      // direct children
      case SNOMED_GENERALIZES -> ">>";   // ancestor-or-self
      case SNOMED_IN -> "^";             // members of the reference set
      default -> throw ApiError.SN301.toApiException(Map.of("operator", operator));
    };
    return prefix + f.getValue();
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
