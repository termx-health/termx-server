package com.kodality.termserver.snomed.ts;

import com.kodality.termserver.snomed.concept.SnomedConcept;
import com.kodality.termserver.snomed.concept.SnomedConceptSearchParams;
import com.kodality.termserver.snomed.snomed.SnomedService;
import com.kodality.termserver.ts.CodeSystemExternalProvider;
import com.kodality.termserver.ts.codesystem.CodeSystemEntityVersion;
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
  public String getCodeSystemId() {
    return SNOMED;
  }
}
