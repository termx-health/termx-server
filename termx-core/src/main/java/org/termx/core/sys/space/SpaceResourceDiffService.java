package org.termx.core.sys.space;

import com.kodality.commons.util.JsonUtil;
import org.termx.core.ApiError;
import org.termx.core.auth.SessionStore;
import org.termx.core.sys.lorque.LorqueProcessService;
import org.termx.core.sys.resource.ResourceDiffService;
import org.termx.core.utils.VirtualThreadExecutor;
import org.termx.sys.lorque.LorqueProcess;
import org.termx.sys.lorque.ProcessResult;
import org.termx.sys.server.TerminologyServer;
import org.termx.core.sys.server.TerminologyServerResourceService;
import org.termx.core.sys.server.TerminologyServerService;
import org.termx.sys.space.diff.SpaceDiff;
import org.termx.sys.space.diff.SpaceDiff.SpaceDiffItem;
import org.termx.sys.spacepackage.PackageVersion.PackageResource;
import org.termx.core.sys.spacepackage.resource.PackageResourceService;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;

@Slf4j
@Singleton
public class SpaceResourceDiffService extends ResourceDiffService {
  private final PackageResourceService packageResourceService;
  private final TerminologyServerService terminologyServerService;
  private final LorqueProcessService lorqueProcessService;

  private final static String process = "space-diff";

  public SpaceResourceDiffService(
      TerminologyServerResourceService terminologyServerResourceService,
      PackageResourceService packageResourceService,
      TerminologyServerService terminologyServerService,
      LorqueProcessService lorqueProcessService
  ) {
    super(terminologyServerResourceService);
    this.packageResourceService = packageResourceService;
    this.terminologyServerService = terminologyServerService;
    this.lorqueProcessService = lorqueProcessService;
  }

  public LorqueProcess findDiff(Long spaceId, String packageCode, String version) {
    LorqueProcess lorqueProcess = lorqueProcessService.start(new LorqueProcess().setProcessName(process));
    CompletableFuture.runAsync(SessionStore.wrap(() -> {
      try {
        ProcessResult result = ProcessResult.text(JsonUtil.toJson(composeDiff(spaceId, packageCode, version)));
        lorqueProcessService.complete(lorqueProcess.getId(), result);
      } catch (Exception e) {
        ProcessResult result = ProcessResult.text(ExceptionUtils.getMessage(e) + "\n" + ExceptionUtils.getStackTrace(e));
        lorqueProcessService.fail(lorqueProcess.getId(), result);
      }
    }), VirtualThreadExecutor.get());
    return lorqueProcess;
  }

  public SpaceDiff composeDiff(Long spaceId, String packageCode, String version) {
    TerminologyServer currentServer = terminologyServerService.loadCurrentInstallation();
    if (currentServer == null) {
      throw ApiError.TC105.toApiException();
    }

    long start = System.currentTimeMillis();
    List<PackageResource> resources = packageResourceService.loadAll(spaceId, packageCode, version);
    log.info("Space diff: Local resources loaded ({} sec)", (System.currentTimeMillis() - start) / 1000);
    start = System.currentTimeMillis();
    List<SpaceDiffItem> items = resources.stream().map(resource -> {
      SpaceDiffItem item = new SpaceDiffItem();
      item.setId(resource.getId());
      item.setResourceId(resource.getResourceId());
      item.setResourceType(resource.getResourceType());
      item.setResourceServer(resource.getTerminologyServer());
      item.setUpToDate(isUpToDate(resource, currentServer.getCode(), resource.getTerminologyServer()));
      return item;
    }).toList();
    log.info("Space diff: Up-to-date checked ({} sec)", (System.currentTimeMillis() - start) / 1000);
    return new SpaceDiff().setItems(items);
  }
}
