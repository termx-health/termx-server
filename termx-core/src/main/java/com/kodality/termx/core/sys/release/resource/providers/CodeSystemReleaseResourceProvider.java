package com.kodality.termx.core.sys.release.resource.providers;

import com.kodality.termx.core.ts.CodeSystemProvider;
import com.kodality.termx.ts.codesystem.CodeSystemVersionReference;
import com.kodality.zmei.fhir.resource.ResourceType;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class CodeSystemReleaseResourceProvider extends ReleaseResourceProvider {
  private final CodeSystemProvider provider;

  @Override
  public String getStatus(String resourceId, String resourceVersion) {
    return provider.loadCodeSystemVersion(resourceId, resourceVersion).map(CodeSystemVersionReference::getStatus).orElse(null);
  }

  @Override
  public void activate(String resourceId, String resourceVersion) {
    provider.activateVersion(resourceId, resourceVersion);
  }

  @Override
  public String getResourceType() {
    return ResourceType.codeSystem;
  }
}
