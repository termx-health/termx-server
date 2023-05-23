package com.kodality.termserver.snomed.ts;

import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.snomed.concept.SnomedConcept;
import com.kodality.termserver.snomed.concept.SnomedConceptSearchParams;
import com.kodality.termserver.snomed.snomed.SnomedService;
import com.kodality.termserver.ts.CodeSystemExternalProvider;
import com.kodality.termserver.ts.codesystem.CodeSystemEntityVersion;
import com.kodality.termserver.ts.codesystem.Concept;
import com.kodality.termserver.ts.codesystem.ConceptQueryParams;
import com.kodality.termserver.ts.codesystem.Designation;
import io.micronaut.core.util.StringUtils;
import java.util.Arrays;
import java.util.List;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class SnomedCodeSystemProvider extends CodeSystemExternalProvider {
  private final SnomedMapper snomedMapper;
  private final SnomedService snomedService;

  private static final String SNOMED = "snomed-ct";

  @Override
  public List<CodeSystemEntityVersion> loadLastVersions(List<String> codes) {
    SnomedConceptSearchParams params = new SnomedConceptSearchParams();
    params.setConceptIds(codes);
    params.setActive(true);
    params.setAll(true);
    List<SnomedConcept> snomedConcepts = snomedService.searchConcepts(params);
    return snomedConcepts.stream().map(snomedMapper::toConceptVersion).toList();
  }

  @Override
  public QueryResult<Concept> searchConcepts(ConceptQueryParams params) {
    if (!SNOMED.equals(params.getCodeSystem())) {
      return QueryResult.empty();
    }

    if (StringUtils.isNotEmpty(params.getDesignationCiEq())) {
      List<Concept> concepts = Arrays.stream(params.getDesignationCiEq().split(",")).filter(term -> term.length() >= 3).distinct().flatMap(term -> {
        SnomedConceptSearchParams p = snomedMapper.toSnomedParams(params).setTerm(term).limit(1);
        return searchConcepts(p).stream().filter(c -> {
          List<Designation> designations = c.getVersions().get(0).getDesignations();
          return designations != null && designations.stream().anyMatch(d -> d.getName() != null && params.getDesignationCiEq().equalsIgnoreCase(d.getName()));
        });
      }).toList();
      return new QueryResult<>(concepts);
    }

    SnomedConceptSearchParams snomedParams = snomedMapper.toSnomedParams(params);
    return new QueryResult<>(searchConcepts(snomedParams));
  }

  private List<Concept> searchConcepts(SnomedConceptSearchParams params) {
    List<SnomedConcept> result = snomedService.searchConcepts(params);
    return result.stream().map(snomedMapper::toConcept).toList();
  }

  @Override
  public String getCodeSystemId() {
    return SNOMED;
  }
}
