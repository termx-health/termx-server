
package org.termx.core.sys.release.resource.providers;

import org.termx.core.ts.MapSetProvider;
import org.termx.ts.mapset.MapSetVersion;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class MapSetReleaseResourceProvider extends ReleaseResourceProvider {
  private final MapSetProvider provider;

  @Override
  public String getStatus(String resourceId, String resourceVersion) {
    return provider.loadMapSetVersion(resourceId, resourceVersion).map(MapSetVersion::getStatus).orElse(null);
  }

  @Override
  public void activate(String resourceId, String resourceVersion) {
    provider.activateVersion(resourceId, resourceVersion);
  }

  @Override
  public String getResourceType() {
    return "MapSet";
  }
}
