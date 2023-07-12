package com.kodality.termx.ts;

import com.kodality.commons.model.QueryResult;
import com.kodality.termx.ts.codesystem.Concept;
import com.kodality.termx.ts.codesystem.ConceptQueryParams;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public abstract class CodeSystemProvider {
  public abstract QueryResult<Concept> searchConcepts(ConceptQueryParams params);
}