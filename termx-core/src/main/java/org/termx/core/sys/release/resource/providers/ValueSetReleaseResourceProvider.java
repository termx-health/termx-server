
package org.termx.core.sys.release.resource.providers;

import org.termx.core.ts.ValueSetProvider;
import org.termx.ts.valueset.ValueSetVersion;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class ValueSetReleaseResourceProvider extends ReleaseResourceProvider {
  private final ValueSetProvider provider;

  @Override
  public String getStatus(String resourceId, String resourceVersion) {
    return provider.loadValueSetVersion(resourceId, resourceVersion).map(ValueSetVersion::getStatus).orElse(null);
  }

  @Override
  public void activate(String resourceId, String resourceVersion) {
    provider.activateVersion(resourceId, resourceVersion);
  }

  @Override
  public String getResourceType() {
    return "ValueSet";
  }
}
