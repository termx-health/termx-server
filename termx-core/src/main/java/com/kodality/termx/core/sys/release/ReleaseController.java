package com.kodality.termx.core.sys.release;

import com.kodality.commons.model.QueryResult;
import com.kodality.termx.core.Privilege;
import com.kodality.termx.core.auth.Authorized;
import com.kodality.termx.core.auth.SessionStore;
import com.kodality.termx.core.sys.provenance.Provenance;
import com.kodality.termx.core.sys.release.resource.ReleaseResourceService;
import com.kodality.termx.sys.release.Release;
import com.kodality.termx.sys.release.ReleaseQueryParams;
import com.kodality.termx.sys.release.ReleaseResource;
import com.kodality.termx.ts.PublicationStatus;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import java.util.List;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;

@Controller("/releases")
@RequiredArgsConstructor
public class ReleaseController {
  private final ReleaseService releaseService;
  private final ReleaseResourceService releaseResourceService;
  private final ReleaseProvenanceService provenanceService;


  //----------------Release----------------

  @Authorized(privilege = Privilege.R_EDIT)
  @Post()
  public Release create(@Valid @Body Release release) {
    provenanceService.provenanceRelease("save", release.getCode(), () -> {
      release.setId(null);
      releaseService.save(release);
    });
    return release;
  }

  @Authorized(Privilege.R_EDIT)
  @Put("{id}")
  public Release update(@PathVariable Long id, @Valid @Body Release release) {
    provenanceService.provenanceRelease("save", release.getCode(), () -> {
      release.setId(id);
      releaseService.save(release);
    });
    return releaseService.save(release);
  }

  @Authorized(Privilege.R_VIEW)
  @Get("{id}")
  public Release load(@PathVariable Long id) {
    return releaseService.load(id);
  }

  @Authorized(Privilege.R_VIEW)
  @Get("/{?params*}")
  public QueryResult<Release> search(ReleaseQueryParams params) {
    params.setPermittedIds(SessionStore.require().getPermittedResourceIds(Privilege.R_VIEW, Long::valueOf));
    return releaseService.query(params);
  }

  @Authorized(Privilege.R_PUBLISH)
  @Post(uri = "/{id}/activate")
  public HttpResponse<?> activate(@PathVariable Long id) {
    provenanceService.provenanceRelease("activate", id, () -> releaseService.changeStatus(id, PublicationStatus.active));
    return HttpResponse.noContent();
  }

  @Authorized(Privilege.R_PUBLISH)
  @Post(uri = "/{id}/retire")
  public HttpResponse<?> retire(@PathVariable Long id) {
    provenanceService.provenanceRelease("retire", id, () -> releaseService.changeStatus(id, PublicationStatus.retired));
    return HttpResponse.noContent();
  }

  @Authorized(Privilege.R_PUBLISH)
  @Post(uri = "/{id}/draft")
  public HttpResponse<?> saveAsDraft(@PathVariable Long id) {
    provenanceService.provenanceRelease("save", id, () -> releaseService.changeStatus(id, PublicationStatus.draft));
    return HttpResponse.noContent();
  }

  //----------------Release Resource----------------

  @Authorized(Privilege.R_VIEW)
  @Get("{id}/resources")
  public List<ReleaseResource> loadResources(@PathVariable Long id) {
    return releaseResourceService.loadAll(id);
  }

  @Authorized(privilege = Privilege.R_EDIT)
  @Post("{id}/resources")
  public HttpResponse<?> createResource(@PathVariable Long id, @Valid @Body ReleaseResource resource) {
    resource.setId(null);
    releaseResourceService.save(id, resource);
    return HttpResponse.ok();
  }

  @Authorized(privilege = Privilege.R_EDIT)
  @Put("{id}/resources/{resourceId}")
  public HttpResponse<?> updateResource(@PathVariable Long id,  @PathVariable Long resourceId, @Valid @Body ReleaseResource resource) {
    resource.setId(resourceId);
    releaseResourceService.save(id, resource);
    return HttpResponse.ok();
  }

  @Authorized(privilege = Privilege.R_EDIT)
  @Delete("{id}/resources/{resourceId}")
  public HttpResponse<?> deleteResource(@PathVariable Long id, @PathVariable Long resourceId) {
    releaseResourceService.cancel(id, resourceId);
    return HttpResponse.ok();
  }


  //----------------Release Provenance----------------

  @Authorized(Privilege.R_VIEW)
  @Get("{id}/provenances")
  public List<Provenance> loadProvenances(@PathVariable Long id) {
    return provenanceService.find(id);
  }
}