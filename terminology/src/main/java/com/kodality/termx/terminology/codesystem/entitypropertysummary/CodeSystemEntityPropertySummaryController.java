package com.kodality.termx.terminology.codesystem.entitypropertysummary;

import com.kodality.termx.Privilege;
import com.kodality.termx.auth.Authorized;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.QueryValue;
import lombok.RequiredArgsConstructor;

@Controller("/ts/code-systems")
@RequiredArgsConstructor
public class CodeSystemEntityPropertySummaryController {
  private final CodeSystemEntityPropertySummaryService service;

  @Authorized(Privilege.CS_VIEW)
  @Get("/{codeSystem}/entity-property-summary")
  public CodeSystemEntityPropertySummary getSummary(@PathVariable String codeSystem, @Nullable @QueryValue String entityPropertyValues) {
    return service.getSummary(codeSystem, null, entityPropertyValues);
  }

  @Authorized(Privilege.CS_VIEW)
  @Get("/{codeSystem}/versions/{version}/entity-property-summary")
  public CodeSystemEntityPropertySummary getSummary(@PathVariable String codeSystem, @PathVariable String version, @Nullable @QueryValue String entityPropertyValues) {
    return service.getSummary(codeSystem, version, entityPropertyValues);
  }

  @Authorized(Privilege.CS_VIEW)
  @Get("/{codeSystem}/entity-property-concept-summary")
  public CodeSystemEntityPropertyConceptSummary getConceptSummary(@PathVariable String codeSystem, @QueryValue Long entityPropertyId, @Nullable @QueryValue String entityPropertyValues) {
    return service.getConceptSummary(codeSystem, null, entityPropertyId, entityPropertyValues);
  }

  @Authorized(Privilege.CS_VIEW)
  @Get("/{codeSystem}/versions/{version}/entity-property-concept-summary")
  public CodeSystemEntityPropertyConceptSummary getConceptSummary(@PathVariable String codeSystem, @PathVariable String version, @QueryValue Long entityPropertyId, @Nullable @QueryValue String entityPropertyValues) {
    return service.getConceptSummary(codeSystem, version, entityPropertyId, entityPropertyValues);
  }
}
