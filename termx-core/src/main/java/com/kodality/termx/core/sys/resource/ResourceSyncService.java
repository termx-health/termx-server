package com.kodality.termx.core.sys.resource;

import com.kodality.commons.exception.ForbiddenException;
import com.kodality.termx.core.ApiError;
import com.kodality.termx.core.auth.SessionStore;
import com.kodality.termx.core.sys.server.TerminologyServerService;
import com.kodality.termx.sys.ResourceReference;
import com.kodality.termx.sys.server.TerminologyServer;
import com.kodality.termx.sys.server.resource.TerminologyServerResourceSyncProvider;
import com.kodality.termx.sys.spacepackage.PackageResourceSyncType;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class ResourceSyncService {
  private final ResourceDiffService diffService;
  private final TerminologyServerService terminologyServerService;
  private final List<TerminologyServerResourceSyncProvider> syncProviders;

  @Transactional
  public void sync(ResourceReference resource, String externalServer, String type, boolean clearSync) {
    TerminologyServer currentServer = terminologyServerService.loadCurrentInstallation();
    sync(resource, currentServer, externalServer, type, clearSync);
  }

  @Transactional
  public void sync(ResourceReference resource, TerminologyServer currentServer, String externalServer, String type, boolean clearSync) {
    if (clearSync && !SessionStore.require().hasPrivilege("*.*.admin")) {
      throw new ForbiddenException("forbidden");
    }
    if (resource == null || externalServer == null) {
      return;
    }
    if (currentServer == null) {
      throw ApiError.TC105.toApiException();
    }

    if (!clearSync) {
      boolean isUpToDate = diffService.isUpToDate(resource, currentServer.getCode(), externalServer);
      if (isUpToDate) {
        return;
      }
    }

    TerminologyServer server = terminologyServerService.load(externalServer);
    TerminologyServerResourceSyncProvider provider = syncProviders.stream()
        .filter(p -> p.checkType(resource.getResourceType()))
        .findFirst()
        .orElseThrow(ApiError.TC102::toApiException);

    if (PackageResourceSyncType.local.equals(type)) {
      provider.sync(server.getId(), currentServer.getId(), resource.getResourceId(), clearSync);
    }
    if (PackageResourceSyncType.external.equals(type)) {
      String id = Stream.of(resource.getResourceId(), resource.getResourceVersion()).filter(Objects::nonNull).collect(Collectors.joining("--"));
      provider.sync(currentServer.getId(), server.getId(), id, clearSync);
    }
  }
}
