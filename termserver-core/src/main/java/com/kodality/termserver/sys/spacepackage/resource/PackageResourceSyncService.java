package com.kodality.termserver.sys.spacepackage.resource;

import com.kodality.termserver.ApiError;
import com.kodality.termserver.sys.server.TerminologyServer;
import com.kodality.termserver.sys.server.TerminologyServerService;
import com.kodality.termserver.sys.server.resource.TerminologyServerResourceSyncProvider;
import com.kodality.termserver.sys.spacepackage.PackageVersion.PackageResource;
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

    provider.syncFrom(server.getRootUrl(), resource.getResourceId());
  }
}
