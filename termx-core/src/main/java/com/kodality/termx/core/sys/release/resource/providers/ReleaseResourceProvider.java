package com.kodality.termx.core.sys.release.resource.providers;

import com.kodality.termx.ts.PublicationStatus;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public abstract class ReleaseResourceProvider {
  public void activate(String resourceType, String resourceId, String resourceVersion) {
    if (resourceType == null || !resourceType.equals(getResourceType()) || !PublicationStatus.draft.equals(getStatus(resourceId, resourceVersion))) {
      return;
    }
    activate(resourceId, resourceVersion);
  }

  public abstract String getStatus(String resourceId, String resourceVersion);
  public abstract void activate(String resourceId, String resourceVersion);

  public abstract String getResourceType();
}
