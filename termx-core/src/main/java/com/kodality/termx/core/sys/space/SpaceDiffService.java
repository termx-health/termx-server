package com.kodality.termx.core.sys.space;

import com.kodality.commons.util.JsonUtil;
import com.kodality.termx.core.ApiError;
import com.kodality.termx.core.auth.SessionStore;
import com.kodality.termx.core.sys.lorque.LorqueProcessService;
import com.kodality.termx.sys.lorque.LorqueProcess;
import com.kodality.termx.sys.lorque.ProcessResult;
import com.kodality.termx.sys.server.TerminologyServer;
import com.kodality.termx.core.sys.server.TerminologyServerResourceService;
import com.kodality.termx.core.sys.server.TerminologyServerService;
import com.kodality.termx.sys.server.resource.TerminologyServerResourceRequest;
import com.kodality.termx.sys.space.diff.SpaceDiff;
import com.kodality.termx.sys.space.diff.SpaceDiff.SpaceDiffItem;
import com.kodality.termx.sys.spacepackage.PackageVersion.PackageResource;
import com.kodality.termx.core.sys.spacepackage.resource.PackageResourceService;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class SpaceDiffService {
  private final PackageResourceService packageResourceService;
  private final TerminologyServerService terminologyServerService;
  private final TerminologyServerResourceService terminologyServerResourceService;
  private final LorqueProcessService lorqueProcessService;

  private final static String process = "space-diff";

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
    }));
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
      item.setUpToDate(isUpToDate(resource, currentServer));
      return item;
    }).toList();
    log.info("Space diff: Up-to-date checked ({} sec)", (System.currentTimeMillis() - start) / 1000);
    return new SpaceDiff().setItems(items);
  }

  public boolean isUpToDate(PackageResource resource, TerminologyServer currentServer) {
    if (resource.getTerminologyServer() == null || resource.getTerminologyServer().equals(currentServer.getCode())) {
      return true;
    }
    TerminologyServerResourceRequest request = new TerminologyServerResourceRequest()
        .setResourceId(resource.getResourceId())
        .setResourceType(resource.getResourceType())
        .setServerCode(currentServer.getCode());
    Map<String, Object> current = JsonUtil.fromJson(terminologyServerResourceService.getResource(request).getResource(), JsonUtil.getMapType(Object.class));
    if (current == null) {
      return false;
    }

    request.setResourceId((String) current.get("id"));
    request.setServerCode(resource.getTerminologyServer());
    Map<String, Object> comparable = JsonUtil.fromJson(terminologyServerResourceService.getResource(request).getResource(), JsonUtil.getMapType(Object.class));
    if (comparable == null) {
      return false;
    }

    current.remove("meta");
    comparable.remove("meta");
    return JsonUtil.toJson(current).equals(JsonUtil.toJson(comparable));
  }
}
