package com.kodality.termx.sys.spacepackage.resource;

import com.kodality.commons.exception.ApiClientException;
import com.kodality.termx.ApiError;
import com.kodality.termx.Privilege;
import com.kodality.termx.auth.Authorized;
import com.kodality.termx.auth.SessionStore;
import com.kodality.termx.sys.job.JobLogResponse;
import com.kodality.termx.sys.job.logger.ImportLogger;
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
import java.util.concurrent.CompletableFuture;
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
  private final ImportLogger importLogger;
  private final PackageResourceService packageResourceService;
  private final PackageResourceSyncService packageResourceSyncService;

  private static final String JOB_TYPE = "package-resource-sync";

  @Authorized(privilege = Privilege.S_VIEW)
  @Get("/{?params*}")
  public List<PackageResource> loadAll(@NotNull @QueryValue Long spaceId, @QueryValue String packageCode, @QueryValue String version) {
    return packageResourceService.loadAll(spaceId, packageCode, version);
  }


  @Authorized(privilege = Privilege.S_EDIT) //TODO: fix this
  @Put("/{id}")
  public PackageResource update(@PathVariable Long id, @Valid @Body PackageResourceSaveRequest request) {
    request.getResource().setId(id);
    return packageResourceService.save(request.getVersionId(), request.getResource());
  }


  @Authorized(privilege = Privilege.S_EDIT)
  @Post(value = "/{id}/sync")
  public HttpResponse<?> importResource(@PathVariable Long id) {
    //TODO: auth?
    JobLogResponse job = importLogger.createJob(JOB_TYPE);
    CompletableFuture.runAsync(SessionStore.wrap(() -> {
      try {
        log.info("Package resource sync started");
        long start = System.currentTimeMillis();
        packageResourceSyncService.sync(id);
        log.info("Package resource sync took " + (System.currentTimeMillis() - start) / 1000 + " seconds");
        importLogger.logImport(job.getJobId());
      } catch (ApiClientException e) {
        log.error("Error while syncing package", e);
        importLogger.logImport(job.getJobId(), e);
      } catch (Exception e) {
        log.error("Error while syncing package", e);
        importLogger.logImport(job.getJobId(), ApiError.TC106.toApiException());
      }
    }));
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

}
