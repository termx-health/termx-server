
package com.kodality.termx.core.sys.release.resource.providers;

import com.kodality.termx.core.ts.ValueSetProvider;
import com.kodality.termx.ts.valueset.ValueSetVersion;
import com.kodality.zmei.fhir.resource.ResourceType;
import javax.inject.Singleton;
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
    return ResourceType.valueSet;
  }
}
