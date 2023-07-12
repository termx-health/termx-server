package com.kodality.termx.terminology.codesystem.concept;

import com.kodality.commons.exception.NotFoundException;
import com.kodality.commons.model.QueryResult;
import com.kodality.termx.Privilege;
import com.kodality.termx.auth.Authorized;
import com.kodality.termx.ts.codesystem.Concept;
import com.kodality.termx.ts.codesystem.ConceptQueryParams;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import lombok.RequiredArgsConstructor;

@Controller("/ts/concepts")
@RequiredArgsConstructor
public class ConceptController {

  private final ConceptService conceptService;

  @Authorized(Privilege.CS_VIEW)
  @Get(uri = "/{id}")
  public Concept getConcept(@PathVariable Long id) {
    return conceptService.load(id).orElseThrow(() -> new NotFoundException("Concept not found: " + id));
  }

  @Authorized(Privilege.CS_VIEW)
  @Get(uri = "{?params*}")
  public QueryResult<Concept> queryConcepts(ConceptQueryParams params) {
    return conceptService.query(params);
  }

}