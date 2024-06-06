package com.kodality.termx.terminology.terminology.codesystem.providers;

import com.kodality.commons.exception.NotFoundException;
import com.kodality.commons.model.QueryResult;
import com.kodality.termx.core.ts.CodeSystemProvider;
import com.kodality.termx.terminology.terminology.codesystem.CodeSystemService;
import com.kodality.termx.terminology.terminology.codesystem.compare.CodeSystemCompareService;
import com.kodality.termx.terminology.terminology.codesystem.concept.ConceptService;
import com.kodality.termx.terminology.terminology.codesystem.version.CodeSystemVersionService;
import com.kodality.termx.ts.codesystem.CodeSystem;
import com.kodality.termx.ts.codesystem.CodeSystemCompareResult;
import com.kodality.termx.ts.codesystem.CodeSystemVersion;
import com.kodality.termx.ts.codesystem.Concept;
import com.kodality.termx.ts.codesystem.ConceptQueryParams;
import java.util.List;
import java.util.Optional;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.tuple.Pair;

@Singleton
@RequiredArgsConstructor
public class TerminologyCodeSystemProvider extends CodeSystemProvider {
  private final ConceptService conceptService;
  private final CodeSystemService codeSystemService;
  private final CodeSystemVersionService codeSystemVersionService;
  private final CodeSystemCompareService codeSystemCompareService;

  @Override
  public CodeSystem loadCodeSystem(String id) {
    return codeSystemService.load(id).orElse(null);
  }

  @Override
  public QueryResult<Concept> searchConcepts(ConceptQueryParams params) {
    return conceptService.query(params);
  }

  @Override
  public Optional<CodeSystemVersion> loadCodeSystemVersion(String codeSystem, String version) {
    return codeSystemVersionService.load(codeSystem, version);
  }
  public void activateVersion(String codeSystem, String version) {
    codeSystemVersionService.activate(codeSystem, version);
  }

  @Override
  public Pair<CodeSystem, CodeSystemCompareResult> compareWithPreviousVersion(String codeSystem, String version) {
    CodeSystemVersion currentVersion = codeSystemVersionService.load(codeSystem, version)
        .orElseThrow(() -> new NotFoundException("CodeSystemVersion not found: " + codeSystem + "--" + version));
    CodeSystemVersion previousVersion = codeSystemVersionService.loadPreviousVersion(codeSystem, version);

    if (previousVersion == null) {
      return null;
    }

    CodeSystem cs = codeSystemService.load(codeSystem).orElseThrow(() -> new NotFoundException("CodeSystem not found: " + codeSystem));
    cs.setVersions(List.of(previousVersion, currentVersion));
    CodeSystemCompareResult compare = codeSystemCompareService.compare(previousVersion.getId(), currentVersion.getId());
    return Pair.of(cs, compare);
  }
}
