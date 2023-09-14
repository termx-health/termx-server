package com.kodality.termx.ts;

import com.kodality.commons.model.QueryResult;
import com.kodality.termx.ts.codesystem.Concept;
import com.kodality.termx.ts.codesystem.ConceptQueryParams;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public abstract class CodeSystemExternalProvider {
  public QueryResult<Concept> searchConcepts(String codeSystem, ConceptQueryParams params) {
    if (codeSystem == null || !codeSystem.equals(getCodeSystemId())) {
      return QueryResult.empty();
    }
    return searchConcepts(params);
  }

  public abstract QueryResult<Concept> searchConcepts(ConceptQueryParams params);

  public abstract String getCodeSystemId();
}
