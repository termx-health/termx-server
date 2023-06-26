package com.kodality.termserver.sys.spacepackage.resource;

import com.kodality.commons.exception.ApiClientException;
import com.kodality.termserver.ApiError;
import com.kodality.termserver.Privilege;
import com.kodality.termserver.auth.Authorized;
import com.kodality.termserver.auth.SessionStore;
import com.kodality.termserver.sys.job.JobLogResponse;
import com.kodality.termserver.sys.job.logger.ImportLogger;
import com.kodality.termserver.sys.spacepackage.PackageVersion.PackageResource;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import java.util.List;
import java.util.Map;
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

  @Authorized(Privilege.P_VIEW)
  @Get("/{?params*}")
  public List<PackageResource> loadAll(Map<String, String> params) {
    return packageResourceService.loadAll(params.get("spaceCode"), params.get("packageCode"), params.get("version"));
  }

  @Authorized(Privilege.P_EDIT)
  @Put("/{id}")
  public PackageResource update(@Parameter Long id, @Valid @Body PackageResourceSaveRequest request) {
    request.getResource().setId(id);
    return packageResourceService.save(request.getVersionId(), request.getResource());
  }

  @Authorized(Privilege.P_EDIT)
  @Post(value = "/{id}/sync")
  public HttpResponse<?> importResource(@Parameter Long id) {
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
  @Introspected
  public static class PackageResourceSaveRequest {
    @NotNull
    private Long versionId;
    @NotNull
    private PackageResource resource;
  }
}
