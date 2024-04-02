package com.kodality.termx.core.sys.space;

import com.kodality.commons.exception.ApiClientException;
import com.kodality.termx.core.ApiError;
import com.kodality.termx.core.auth.SessionStore;
import com.kodality.termx.core.sys.job.logger.ImportLog;
import com.kodality.termx.core.sys.job.logger.ImportLogger;
import com.kodality.termx.core.sys.server.TerminologyServerService;
import com.kodality.termx.core.sys.spacepackage.resource.PackageResourceService;
import com.kodality.termx.core.sys.spacepackage.resource.PackageResourceSyncService;
import com.kodality.termx.sys.job.JobLogResponse;
import com.kodality.termx.sys.server.TerminologyServer;
import com.kodality.termx.sys.spacepackage.PackageResourceSyncType;
import com.kodality.termx.sys.spacepackage.PackageVersion.PackageResource;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class SpaceSyncService {
  private final ImportLogger importLogger;
  private final SpaceDiffService diffService;
  private final TerminologyServerService terminologyServerService;
  private final PackageResourceService packageResourceService;
  private final PackageResourceSyncService packageResourceSyncService;

  private static final String JOB = "space-resource-sync";
  public JobLogResponse syncResources(Long spaceId, String packageCode, String version, boolean clearSync) {
    JobLogResponse job = importLogger.createJob(JOB);
    CompletableFuture.runAsync(SessionStore.wrap(() -> {
      try {
        ImportLog log = sync(spaceId, packageCode, version, clearSync);
        importLogger.logImport(job.getJobId(), log);
      } catch (ApiClientException e) {
        importLogger.logImport(job.getJobId(), e);
      } catch (Exception e) {
        importLogger.logImport(job.getJobId(), ApiError.TC111.toApiException());
      }
    }));
    return job;
  }

  public ImportLog sync(Long spaceId, String packageCode, String version, boolean clearSync) {
    TerminologyServer currentServer = terminologyServerService.loadCurrentInstallation();
    if (currentServer == null) {
      throw ApiError.TC105.toApiException();
    }

    List<PackageResource> resources = packageResourceService.loadAll(spaceId, packageCode, version);
    ImportLog log = new ImportLog().setErrors(new ArrayList<>()).setSuccesses(new ArrayList<>());
    resources.forEach(resource -> {
      if (!clearSync) {
        boolean isUpToDate = diffService.isUpToDate(resource, currentServer);
        if (isUpToDate) {
          return;
        }
      }
      try {
        packageResourceSyncService.sync(resource.getId(), PackageResourceSyncType.external, clearSync);
        log.getSuccesses().add(String.join("|", resource.getResourceId(), resource.getResourceType()));
      } catch (Exception e) {
        log.getErrors().add(String.join("|", resource.getResourceId(), resource.getResourceType(), e.getMessage()));
      }
    });
    return log;
  }
}
