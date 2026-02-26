package com.kodality.termx.ucum.ts;

import com.kodality.commons.model.QueryResult;
import com.kodality.termx.core.ts.CodeSystemExternalProvider;
import com.kodality.termx.ts.codesystem.Concept;
import com.kodality.termx.ts.codesystem.ConceptQueryParams;
import jakarta.inject.Singleton;

@Singleton
public class UcumCodeSystemProvider extends CodeSystemExternalProvider {
  private final UcumConceptResolver ucumConceptResolver;

  private static final String UCUM = "ucum";

  public UcumCodeSystemProvider(UcumConceptResolver ucumConceptResolver) {
    this.ucumConceptResolver = ucumConceptResolver;
  }

  @Override
  public QueryResult<Concept> searchConcepts(ConceptQueryParams params) {
    return ucumConceptResolver.search(params);
  }

  @Override
  public String getCodeSystemId() {
    return UCUM;
  }
}
