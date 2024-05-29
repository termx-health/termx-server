package com.kodality.termx.core.sys.spacepackage.resource;

import com.kodality.termx.core.Privilege;
import com.kodality.termx.core.auth.Authorized;
import com.kodality.termx.sys.job.JobLogResponse;
import com.kodality.termx.sys.spacepackage.PackageVersion.PackageResource;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.annotation.QueryValue;
import java.util.List;
import javax.annotation.Nullable;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller("/package-resources")
@RequiredArgsConstructor
public class PackageResourceController {
  private final PackageResourceService packageResourceService;
  private final PackageResourceSyncService packageResourceSyncService;

  @Authorized(privilege = Privilege.S_VIEW)
  @Get("/{?params*}")
  public List<PackageResource> loadAll(@NotNull @QueryValue Long spaceId, @Nullable @QueryValue String packageCode, @Nullable @QueryValue String version) {
    return packageResourceService.loadAll(spaceId, packageCode, version);
  }


  @Authorized(privilege = Privilege.S_EDIT) //TODO: fix this
  @Put("/{id}")
  public PackageResource update(@PathVariable Long id, @Valid @Body PackageResourceSaveRequest request) {
    request.getResource().setId(id);
    return packageResourceService.save(request.getVersionId(), request.getResource());
  }

  @Authorized(privilege = Privilege.S_EDIT)
  @Post("/change-server")
  public HttpResponse<?> update(@Valid @Body PackageResourceChangeServerRequest request) {
    packageResourceService.changeServer(request.getResourceIds(), request.getTerminologyServer());
    return HttpResponse.ok();
  }


  @Authorized(privilege = Privilege.S_EDIT)
  @Post(value = "/{id}/sync")
  public HttpResponse<?> importResource(@PathVariable Long id, @Valid @Body PackageResourceSyncRequest request) {
    //TODO: auth?
    JobLogResponse job = packageResourceSyncService.sync(id, request.getType(), request.getResourceVersion(), false);
    return HttpResponse.ok(job);
  }

  @Getter
  @Setter
  public static class PackageResourceSaveRequest {
    @NotNull
    private Long versionId;
    @NotNull
    private PackageResource resource;
  }

  @Getter
  @Setter
  public static class PackageResourceChangeServerRequest {
    @NotNull
    private List<Long> resourceIds;
    @NotNull
    private String terminologyServer;
  }


  @Getter
  @Setter
  public static class PackageResourceSyncRequest {
    @NotNull
    private String type;
    private String resourceVersion;
  }

}
