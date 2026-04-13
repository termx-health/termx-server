package org.termx.terminology.terminology.codesystem.concept;

import com.kodality.commons.exception.NotFoundException;
import com.kodality.commons.model.QueryResult;
import org.termx.terminology.Privilege;
import org.termx.core.auth.Authorized;
import org.termx.core.auth.SessionStore;
import org.termx.ts.codesystem.Concept;
import org.termx.ts.codesystem.ConceptQueryParams;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import lombok.RequiredArgsConstructor;

@Controller("/ts/concepts")
@RequiredArgsConstructor
public class ConceptController {

  private final ConceptService conceptService;

  @Authorized(privilege = Privilege.CS_VIEW)
  @Get(uri = "/{id}{?params*}")
  public Concept getConcept(@PathVariable Long id, ConceptQueryParams params) {
    Concept c = conceptService.load(id).map(concept -> conceptService.decorate(concept, concept.getCodeSystem(), params))
        .orElseThrow(() -> new NotFoundException("Concept not found: " + id));
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
