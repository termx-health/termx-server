package com.kodality.termx.core.sys.release;

import com.kodality.termx.core.sys.job.logger.ImportLog;
import com.kodality.termx.core.sys.job.logger.ImportLogger;
import com.kodality.termx.core.sys.release.resource.ReleaseResourceService;
import com.kodality.termx.core.sys.resource.ResourceDiffService;
import com.kodality.termx.core.sys.resource.ResourceSyncService;
import com.kodality.termx.core.sys.server.TerminologyServerService;
import com.kodality.termx.sys.job.JobLogResponse;
import com.kodality.termx.sys.release.Release;
import com.kodality.termx.sys.release.ReleaseResource;
import com.kodality.termx.sys.server.TerminologyServer;
import com.kodality.termx.sys.spacepackage.PackageResourceSyncType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class ReleaseSyncService {
  private final ImportLogger importLogger;
  private final ResourceSyncService resourceSyncService;
  private final ResourceDiffService resourceDiffService;
  private final ReleaseService releaseService;
  private final ReleaseResourceService releaseResourceService;
  private final TerminologyServerService terminologyServerService;

  private static final String JOB_SYNC = "release-resource-sync";
  private static final String JOB_VALIDATE = "release-resource-sync-validtaion";

  public JobLogResponse syncResources(Long releaseId, Long resourceId) {
    Map<String, Long> params = new HashMap<>();
    params.put("releaseId", releaseId);
    if (resourceId != null) {
      params.put("resourceId", resourceId);
    }
    return importLogger.runJob(JOB_SYNC, params, this::sync);
  }

  public JobLogResponse validateSync(Long releaseId) {
    return importLogger.runJob(JOB_VALIDATE, releaseId, this::validate);
  }

  public ImportLog sync(Map<String, Long> params) {
    Release release = releaseService.load(params.get("releaseId"));
    ImportLog log = new ImportLog().setErrors(new ArrayList<>()).setSuccesses(new ArrayList<>());
    TerminologyServer currentServer = terminologyServerService.loadCurrentInstallation();

    List<ReleaseResource> resources = params.containsKey("resourceId") ?
        release.getResources().stream().filter(r -> r.getId().equals(params.get("resourceId"))).toList() :
        release.getResources();
    resources.forEach(resource -> {
      try {
        resourceSyncService.sync(resource, currentServer, release.getTerminologyServer(), PackageResourceSyncType.external, false);
        log.getSuccesses().add(String.join("|", resource.getResourceId(), resource.getResourceType()));
      } catch (Exception e) {
        log.getErrors().add(String.join("|", resource.getResourceId(), resource.getResourceType(), e.getMessage()));
      }
    });
    return log;
  }

  public ImportLog validate(Long releaseId) {
    Release release = releaseService.load(releaseId);
    List<ReleaseResource> resources = releaseResourceService.loadAll(releaseId);
    ImportLog log = new ImportLog().setErrors(new ArrayList<>()).setSuccesses(new ArrayList<>()).setWarnings(new ArrayList<>());
    TerminologyServer currentServer = terminologyServerService.loadCurrentInstallation();
    resources.forEach(resource -> {
      try {
        boolean isUpToDate = resourceDiffService.isUpToDate(resource, currentServer.getCode(), release.getTerminologyServer());
        if (isUpToDate) {
          log.getSuccesses().add(String.valueOf(resource.getId()));
        } else {
          log.getWarnings().add(String.valueOf(resource.getId()));
        }
      } catch (Exception e) {
        log.getErrors().add(String.join("|", resource.getResourceId(), resource.getResourceType(), e.getMessage()));
      }
    });
    return log;
  }
}
