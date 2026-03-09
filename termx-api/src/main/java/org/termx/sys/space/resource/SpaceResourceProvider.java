package org.termx.sys.space.resource;

import org.termx.sys.server.TerminologyServer;
import org.termx.sys.spacepackage.PackageVersion.PackageResource;
import java.util.List;

public interface SpaceResourceProvider {
  String getType();

  List<String> importResource(List<PackageResource> resources, List<TerminologyServer> extServers);

  List<String> queryExistingResources(List<String> ids);
}
