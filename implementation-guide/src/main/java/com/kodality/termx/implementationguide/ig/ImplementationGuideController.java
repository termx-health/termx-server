package com.kodality.termx.implementationguide.ig;

import com.kodality.commons.exception.NotFoundException;
import com.kodality.commons.model.QueryResult;
import com.kodality.termx.core.auth.Authorized;
import com.kodality.termx.core.auth.SessionStore;
import com.kodality.termx.implementationguide.Privilege;
import com.kodality.termx.implementationguide.ig.version.ImplementationGuideVersion;
import com.kodality.termx.implementationguide.ig.version.ImplementationGuideVersionQueryParams;
import com.kodality.termx.implementationguide.ig.version.ImplementationGuideVersionService;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.validation.Validated;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;

@Validated
@RequiredArgsConstructor
@Controller("/implementation-guides")
public class ImplementationGuideController {
  private final ImplementationGuideService igService;
  private final ImplementationGuideVersionService igVersionService;
  private final ImplementationGuideProvenanceService provenanceService;

  @Authorized(Privilege.IG_VIEW)
  @Get(uri = "{?params*}")
  public QueryResult<ImplementationGuide> query(ImplementationGuideQueryParams params) {
    params.setPermittedIds(SessionStore.require().getPermittedResourceIds(Privilege.IG_VIEW));
    return igService.query(params);
  }

  @Authorized(Privilege.IG_VIEW)
  @Get(uri = "/{ig}")
  public ImplementationGuide load(@PathVariable String ig) {
    return igService.load(ig).orElseThrow(() -> new NotFoundException("ImplementationGuide not found: " + ig));
  }

  @Authorized(Privilege.IG_EDIT)
  @Post("/transaction")
  public HttpResponse<?> save(@Body @Valid ImplementationGuideTransactionRequest request) {
    SessionStore.require().checkPermitted(request.getImplementationGuide().getId(), Privilege.IG_EDIT);
    provenanceService.provenanceImplementationGuideTransactionRequest("save", request, () -> igService.save(request));
    return HttpResponse.created(request.getImplementationGuide());
  }


  //----------------CodeSystem Version----------------

  @Authorized(Privilege.IG_VIEW)
  @Get(uri = "/{ig}/versions{?params*}")
  public QueryResult<ImplementationGuideVersion> queryVersions(@PathVariable String ig, ImplementationGuideVersionQueryParams params) {
    params.setPermittedImplementationGuideIds(SessionStore.require().getPermittedResourceIds(Privilege.IG_VIEW));
    params.setImplementationGuideIds(ig);
    return igVersionService.query(params);
  }

  @Authorized(Privilege.IG_VIEW)
  @Get(uri = "/{ig}/versions/{version}")
  public ImplementationGuideVersion getCodeSystemVersion(@PathVariable String ig, @PathVariable String version) {
    return igVersionService.load(ig, version).orElseThrow(() -> new NotFoundException("ImplementationGuide version not found: " + ig + "|" + version));
  }
}
