package com.kodality.termx.terminology.terminology.codesystem.concept;

import com.kodality.commons.exception.NotFoundException;
import com.kodality.commons.model.QueryResult;
import com.kodality.termx.terminology.Privilege;
import com.kodality.termx.core.auth.Authorized;
import com.kodality.termx.core.auth.SessionStore;
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

  @Authorized(privilege = Privilege.CS_VIEW)
  @Get(uri = "/{id}")
  public Concept getConcept(@PathVariable Long id) {
    Concept c = conceptService.load(id).orElseThrow(() -> new NotFoundException("Concept not found: " + id));
    SessionStore.require().checkPermitted(c.getCodeSystem(), Privilege.CS_VIEW);
    return c;
  }

  @Authorized(privilege = Privilege.CS_VIEW)
  @Get(uri = "{?params*}")
  public QueryResult<Concept> queryConcepts(ConceptQueryParams params) {
    params.setPermittedCodeSystems(SessionStore.require().getPermittedResourceIds(Privilege.CS_VIEW));
    return conceptService.query(params);
  }

}
