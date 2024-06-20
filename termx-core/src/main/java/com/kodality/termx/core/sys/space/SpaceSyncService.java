package com.kodality.termx.core.sys.space;

import com.kodality.termx.core.sys.job.logger.ImportLog;
import com.kodality.termx.core.sys.job.logger.ImportLogger;
import com.kodality.termx.core.sys.resource.ResourceSyncService;
import com.kodality.termx.core.sys.server.TerminologyServerService;
import com.kodality.termx.core.sys.spacepackage.resource.PackageResourceService;
import com.kodality.termx.sys.job.JobLogResponse;
import com.kodality.termx.sys.server.TerminologyServer;
import com.kodality.termx.sys.spacepackage.PackageResourceSyncType;
import com.kodality.termx.sys.spacepackage.PackageVersion.PackageResource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class SpaceSyncService {
  private final ImportLogger importLogger;
  private final PackageResourceService packageResourceService;
  private final ResourceSyncService resourceSyncService;
  private final TerminologyServerService terminologyServerService;

  private static final String JOB_TYPE = "space-resource-sync";
  public JobLogResponse syncResources(Long spaceId, String packageCode, String version, boolean clearSync) {
    Map<String, Object> params = Map.of("spaceId", spaceId, "packageCode", packageCode, "version", version, "clearSync", clearSync);
    return importLogger.runJob(JOB_TYPE, params, this::sync);
  }

  public ImportLog sync(Map<String, Object> params) {
    Long spaceId = (Long) params.get("spaceId");
    String packageCode = (String) params.get("packageCode");
    String version = (String) params.get("version");
    boolean clearSync = (boolean) params.get("clearSync");

    List<PackageResource> resources = packageResourceService.loadAll(spaceId, packageCode, version);
    ImportLog log = new ImportLog().setErrors(new ArrayList<>()).setSuccesses(new ArrayList<>());
    TerminologyServer currentServer = terminologyServerService.loadCurrentInstallation();
    resources.forEach(resource -> {
      try {
        resourceSyncService.sync(resource, currentServer, resource.getTerminologyServer(), PackageResourceSyncType.external, clearSync);
        log.getSuccesses().add(String.join("|", resource.getResourceId(), resource.getResourceType()));
      } catch (Exception e) {
        log.getErrors().add(String.join("|", resource.getResourceId(), resource.getResourceType(), e.getMessage()));
      }
    });
    return log;
  }
}
