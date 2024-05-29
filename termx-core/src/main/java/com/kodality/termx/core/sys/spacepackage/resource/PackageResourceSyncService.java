package com.kodality.termx.core.sys.spacepackage.resource;

import com.kodality.termx.core.sys.job.logger.ImportLog;
import com.kodality.termx.core.sys.job.logger.ImportLogger;
import com.kodality.termx.core.sys.resource.ResourceSyncService;
import com.kodality.termx.sys.job.JobLogResponse;
import com.kodality.termx.sys.spacepackage.PackageVersion.PackageResource;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class PackageResourceSyncService {
  private final ImportLogger importLogger;
  private final PackageResourceService packageResourceService;
  private final ResourceSyncService resourceSyncService;
  private static final String JOB_TYPE = "package-resource-sync";

  @Transactional
  public JobLogResponse sync(Long id, String type, String resourceVersion, boolean clearSync) {
    Map<String, Object> params = Map.of("resourceId", id, "type", type, "resourceVersion", Optional.ofNullable(resourceVersion), "clearSync", clearSync);
    return importLogger.runJob(JOB_TYPE, params, this::sync);
  }

  public ImportLog sync(Map<String, Object> params) {
    Long resourceId = (Long) params.get("resourceId");
    String type = (String) params.get("type");
    String resourceVersion = ((Optional<String>) params.get("resourceVersion")).orElse(null);
    boolean clearSync = (boolean) params.get("clearSync");

    PackageResource resource = packageResourceService.load(resourceId);
    resource.setResourceVersion(resourceVersion);
    ImportLog log = new ImportLog().setErrors(new ArrayList<>()).setSuccesses(new ArrayList<>());
    try {
      resourceSyncService.sync(resource, resource.getTerminologyServer(), type, clearSync);
      log.getSuccesses().add(String.join("|", resource.getResourceId(), resource.getResourceType()));
    } catch (Exception e) {
      log.getErrors().add(String.join("|", resource.getResourceId(), resource.getResourceType(), e.getMessage()));
    }
    return log;
  }
}
