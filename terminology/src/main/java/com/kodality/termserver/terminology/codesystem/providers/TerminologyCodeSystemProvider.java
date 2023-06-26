package com.kodality.termserver.terminology.codesystem.providers;

import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.terminology.codesystem.concept.ConceptService;
import com.kodality.termserver.ts.CodeSystemProvider;
import com.kodality.termserver.ts.codesystem.Concept;
import com.kodality.termserver.ts.codesystem.ConceptQueryParams;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class TerminologyCodeSystemProvider extends CodeSystemProvider {
  private final ConceptService conceptService;

  @Override
  public QueryResult<Concept> searchConcepts(ConceptQueryParams params) {
    return conceptService.query(params);
  }
}
