package com.kodality.termserver.snomed.ts;

import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.snomed.concept.SnomedConcept;
import com.kodality.termserver.snomed.concept.SnomedConceptSearchParams;
import com.kodality.termserver.snomed.snomed.SnomedService;
import com.kodality.termserver.ts.CodeSystemExternalProvider;
import com.kodality.termserver.ts.codesystem.CodeSystemEntityVersion;
import com.kodality.termserver.ts.codesystem.Concept;
import com.kodality.termserver.ts.codesystem.ConceptQueryParams;
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
    SnomedConceptSearchParams snomedParams = new SnomedConceptSearchParams();
    snomedParams.setConceptIds(StringUtils.isNotEmpty(params.getCode()) ? Arrays.stream(params.getCode().split(",")).toList() : List.of());
    snomedParams.setTerm(params.getTextContains());
    snomedParams.setActive(true);
    snomedParams.setLimit(params.getLimit());
    List<SnomedConcept> result = snomedService.searchConcepts(snomedParams);
    return new QueryResult<>(result.stream().map(snomedMapper::toConcept).toList());
  }

  @Override
  public String getCodeSystemId() {
    return SNOMED;
  }
}
