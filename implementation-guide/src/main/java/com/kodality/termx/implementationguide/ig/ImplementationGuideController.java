package com.kodality.termx.implementationguide.ig;

import com.kodality.commons.exception.NotFoundException;
import com.kodality.commons.model.QueryResult;
import com.kodality.termx.core.auth.Authorized;
import com.kodality.termx.core.auth.SessionStore;
import com.kodality.termx.core.sys.provenance.Provenance;
import com.kodality.termx.core.sys.provenance.Provenance.ProvenanceChange;
import com.kodality.termx.implementationguide.Privilege;
import com.kodality.termx.implementationguide.ig.version.ImplementationGuideVersion;
import com.kodality.termx.implementationguide.ig.version.ImplementationGuideVersionQueryParams;
import com.kodality.termx.implementationguide.ig.version.ImplementationGuideVersionService;
import com.kodality.termx.implementationguide.ig.version.group.ImplementationGuideGroup;
import com.kodality.termx.implementationguide.ig.version.resource.ImplementationGuideResource;
import com.kodality.termx.implementationguide.ig.version.resource.ImplementationGuideResourceService;
import com.kodality.termx.ts.PublicationStatus;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.validation.Validated;
import java.util.List;
import java.util.Map;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;

@Validated
@RequiredArgsConstructor
@Controller("/implementation-guides")
public class ImplementationGuideController {
  private final ImplementationGuideService igService;
  private final ImplementationGuideVersionService igVersionService;
  private final ImplementationGuideResourceService igResourceService;
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

  @Authorized(Privilege.IG_EDIT)
  @Post(uri = "/{ig}/change-id")
  public HttpResponse<?> changeId(@PathVariable String ig, @Valid @Body Map<String, String> body) {
    String newId = body.get("id");
    igService.changeId(ig, newId);
    provenanceService.create(new Provenance("change-id", "ImplementationGuide", newId).setChanges(Map.of("id", ProvenanceChange.of(ig, newId))));
    return HttpResponse.ok();
  }

  @Authorized(Privilege.IG_PUBLISH)
  @Delete(uri = "/{ig}")
  public HttpResponse<?> delete(@PathVariable String ig) {
    igService.cancel(ig);
    provenanceService.create(new Provenance("delete", "ImplementationGuide", ig));
    return HttpResponse.ok();
  }


  //----------------ImplementationGuide Version----------------

  @Authorized(Privilege.IG_VIEW)
  @Get(uri = "/{ig}/versions{?params*}")
  public QueryResult<ImplementationGuideVersion> queryVersions(@PathVariable String ig, ImplementationGuideVersionQueryParams params) {
    params.setPermittedImplementationGuideIds(SessionStore.require().getPermittedResourceIds(Privilege.IG_VIEW));
    params.setImplementationGuideIds(ig);
    return igVersionService.query(params);
  }

  @Authorized(Privilege.IG_VIEW)
  @Get(uri = "/{ig}/versions/{version}")
  public ImplementationGuideVersion getVersion(@PathVariable String ig, @PathVariable String version) {
    return igVersionService.load(ig, version).orElseThrow(() -> new NotFoundException("ImplementationGuide version not found: " + ig + "|" + version));
  }

  @Authorized(Privilege.IG_EDIT)
  @Post(uri = "/{ig}/versions")
  public HttpResponse<?> createVersion(@PathVariable String ig, @Body @Valid ImplementationGuideVersion igVersion) {
    igVersion.setId(null);
    igVersion.setImplementationGuide(ig);
    provenanceService.provenanceImplementationGuideVersion("save", ig, igVersion.getVersion(), () -> igVersionService.save(igVersion));
    return HttpResponse.created(igVersion);
  }

  @Authorized(Privilege.IG_EDIT)
  @Put(uri = "/{ig}/versions/{version}")
  public HttpResponse<?> updateVersion(@PathVariable String ig, @PathVariable String version,
                                                 @Body @Valid ImplementationGuideVersion igVersion) {
    igVersion.setVersion(version);
    igVersion.setImplementationGuide(ig);
    provenanceService.provenanceImplementationGuideVersion("save", ig, version, () -> igVersionService.save(igVersion));
    return HttpResponse.created(igVersion);
  }

  @Authorized(Privilege.IG_PUBLISH)
  @Post(uri = "/{ig}/versions/{version}/activate")
  public HttpResponse<?> activateVersion(@PathVariable String ig, @PathVariable String version) {
    provenanceService.provenanceImplementationGuideVersion("activate", ig, version, () -> igVersionService.changeStatus(ig, version, PublicationStatus.active));
    return HttpResponse.noContent();
  }

  @Authorized(Privilege.IG_PUBLISH)
  @Post(uri = "/{ig}/versions/{version}/retire")
  public HttpResponse<?> retireVersion(@PathVariable String ig, @PathVariable String version) {
    provenanceService.provenanceImplementationGuideVersion("retire", ig, version, () -> igVersionService.changeStatus(ig, version, PublicationStatus.retired));
    return HttpResponse.noContent();
  }

  @Authorized(Privilege.IG_PUBLISH)
  @Post(uri = "/{ig}/versions/{version}/draft")
  public HttpResponse<?> saveAsDraftVersion(@PathVariable String ig, @PathVariable String version) {
    provenanceService.provenanceImplementationGuideVersion("save", ig, version, () -> igVersionService.changeStatus(ig, version, PublicationStatus.draft));
    return HttpResponse.noContent();
  }

  @Authorized(Privilege.IG_EDIT)
  @Post("/{ig}/versions/{version}/groups")
  public HttpResponse<?> saveVersionGroups(@PathVariable String ig, @PathVariable String version, @Body List<ImplementationGuideGroup> groups) {
    provenanceService.provenanceImplementationGuideVersion("save-groups", ig, version, () -> igVersionService.saveGroups(ig, version, groups));
    return HttpResponse.ok();
  }

  @Authorized(Privilege.IG_EDIT)
  @Get("/{ig}/versions/{version}/resources")
  public List<ImplementationGuideResource> loadVersionResources(@PathVariable String ig, @PathVariable String version) {
    return igVersionService.loadResources(ig, version);
  }

  @Authorized(Privilege.IG_EDIT)
  @Post("/{ig}/versions/{version}/resources")
  public HttpResponse<?> saveVersionResources(@PathVariable String ig, @PathVariable String version, @Body List<ImplementationGuideResource> resources) {
    provenanceService.provenanceImplementationGuideVersion("save-resources", ig, version, () -> igVersionService.saveResources(ig, version, resources));
    return HttpResponse.ok();
  }


  //----------------Provenances----------------

  @Authorized(Privilege.IG_VIEW)
  @Get(uri = "/{ig}/provenances")
  public List<Provenance> queryProvenances(@PathVariable String ig, @Nullable @QueryValue String version) {
    return provenanceService.find(ig, version);
  }

}
