package org.termx.snomed.ts;

import com.kodality.commons.model.QueryResult;
import org.termx.snomed.concept.SnomedConcept;
import org.termx.snomed.concept.SnomedConceptSearchParams;
import org.termx.snomed.integration.SnomedService;
import org.termx.core.ts.CodeSystemExternalProvider;
import org.termx.core.ts.CodeSystemProvider;
import org.termx.ts.codesystem.Concept;
import org.termx.ts.codesystem.ConceptQueryParams;
import org.termx.ts.codesystem.Designation;
import io.micronaut.context.BeanProvider;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class SnomedCodeSystemProvider extends CodeSystemExternalProvider {
  private final SnomedMapper snomedMapper;
  private final SnomedService snomedService;
  // Lazy: SnomedCodeSystemProvider is itself a CodeSystemExternalProvider, and the concrete
  // CodeSystemProvider (TerminologyCodeSystemProvider) depends on ConceptService, which injects
  // List<CodeSystemExternalProvider> — a direct injection here would form a DI cycle. BeanProvider
  // defers resolution to call time, breaking the cycle.
  private final BeanProvider<CodeSystemProvider> codeSystemProvider;

  private static final String SNOMED = "snomed-ct";

  @Override
  public QueryResult<Concept> searchConcepts(ConceptQueryParams params) {
    if (!SNOMED.equals(params.getCodeSystem())) {
      return QueryResult.empty();
    }

    // Route to the requested SNOMED edition branch (derived from the version URI). Without this the
    // load falls to the default International branch, whose Accept-Language carries only the
    // International languages — so edition-specific designations (e.g. Estonian) are never returned.
    String branch = getBranch(params.getCodeSystemVersion());

    if (StringUtils.isNotEmpty(params.getDesignationCiEq())) {
      List<Concept> concepts = Arrays.stream(params.getDesignationCiEq().split(",")).filter(term -> term.length() >= 3).distinct().flatMap(term -> {
        SnomedConceptSearchParams p = snomedMapper.toSnomedParams(params).setTerm(term).setBranch(branch).limit(1);
        return searchConcepts(p).stream().filter(c -> {
          List<Designation> designations = c.getVersions().getFirst().getDesignations();
          return designations != null && designations.stream().anyMatch(d -> d.getName() != null && params.getDesignationCiEq().equalsIgnoreCase(d.getName()));
        });
      }).toList();
      return new QueryResult<>(concepts);
    }

    SnomedConceptSearchParams snomedParams = snomedMapper.toSnomedParams(params).setBranch(branch);
    return new QueryResult<>(searchConcepts(snomedParams));
  }

  private List<Concept> searchConcepts(SnomedConceptSearchParams params) {
    if (CollectionUtils.isNotEmpty(params.getConceptIds()) && params.getTerm() == null) {
      // TODO: this loads concepts via Snowstorm's /concepts SEARCH endpoint, which only returns the
      // language-resolved pt + fsn (SnomedMapper.toConceptVersion then maps just those two). So a
      // CodeSystem/$lookup with properties=designation returns the preferred term + FSN, NOT every
      // designation of the concept (e.g. for a SNOMED edition it yields the edition PT but misses the
      // edition's additional synonyms in the same language).
      // If full designations are ever required here: fetch them per concept from the browser endpoint
      // and let SnomedMapper.toConceptVersion map snomedConcept.getDescriptions() (it already prefers
      // descriptions when present). SnomedService.loadDescriptions(branch, conceptIds) +
      // SnowstormClient already do this for the value-set expand path (see SnomedValueSetExpandProvider
      // .getDescriptions / findDesignations) — reuse that to populate descriptions before mapping.
      return snomedService.loadConcepts(params.getConceptIds(), params.getBranch()).stream().map(snomedMapper::toConcept).toList();
    }
    return snomedService.searchConcepts(params).stream().map(snomedMapper::toConcept).toList();
  }

  private String getBranch(String versionUri) {
    if (StringUtils.isEmpty(versionUri)) {
      return null;
    }

    String[] uri = versionUri.split("/");
    if (uri.length < 5) {
      return null;
    }

    String moduleId = uri[4];
    String branchPath = loadModules().get(moduleId);
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
    return codeSystemProvider.get().searchConcepts(params).getData().stream()
        .filter(c -> c.getLastVersion().map(v -> v.getPropertyValue("moduleId").isPresent() && v.getPropertyValue("branchPath").isPresent()).orElse(false))
        .collect(Collectors.toMap(c -> (String) c.getLastVersion().get().getPropertyValue("moduleId").get(), c -> (String) c.getLastVersion().get().getPropertyValue("branchPath").get()));
  }

  @Override
  public String getCodeSystemId() {
    return SNOMED;
  }
}
