package com.kodality.termx.core.sys.spacepackage.resource;

import com.kodality.termx.core.ApiError;
import com.kodality.termx.sys.server.TerminologyServer;
import com.kodality.termx.core.sys.server.TerminologyServerService;
import com.kodality.termx.sys.server.resource.TerminologyServerResourceSyncProvider;
import com.kodality.termx.sys.spacepackage.PackageVersion.PackageResource;
import java.util.List;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class PackageResourceSyncService {
  private final PackageResourceService packageResourceService;
  private final TerminologyServerService terminologyServerService;
  private final List<TerminologyServerResourceSyncProvider> syncProviders;

  @Transactional
  public void sync(Long id) {
    PackageResource resource = packageResourceService.load(id);
    if (resource == null || resource.getTerminologyServer() == null) {
      return;
    }

    TerminologyServer server = terminologyServerService.load(resource.getTerminologyServer());
    TerminologyServerResourceSyncProvider provider = syncProviders.stream()
        .filter(p -> p.getType().equals(resource.getResourceType()))
        .findFirst()
        .orElseThrow(ApiError.TC102::toApiException);

    provider.syncFrom(server.getId(), resource.getResourceId());
  }
}
