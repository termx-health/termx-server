package com.kodality.termx.terminology.terminology.codesystem.providers;

import com.kodality.commons.model.QueryResult;
import com.kodality.termx.terminology.terminology.codesystem.CodeSystemService;
import com.kodality.termx.terminology.terminology.codesystem.concept.ConceptService;
import com.kodality.termx.core.ts.CodeSystemProvider;
import com.kodality.termx.ts.codesystem.CodeSystem;
import com.kodality.termx.ts.codesystem.Concept;
import com.kodality.termx.ts.codesystem.ConceptQueryParams;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class TerminologyCodeSystemProvider extends CodeSystemProvider {
  private final ConceptService conceptService;
  private final CodeSystemService codeSystemService;

  @Override
  public CodeSystem loadCodeSystem(String id) {
    return codeSystemService.load(id).orElse(null);
  }

  @Override
  public QueryResult<Concept> searchConcepts(ConceptQueryParams params) {
    return conceptService.query(params);
  }
}
