package com.kodality.termx.sys.space.resource;

import com.kodality.termx.sys.server.TerminologyServer;
import com.kodality.termx.sys.spacepackage.PackageVersion.PackageResource;
import java.util.List;

public interface SpaceResourceProvider {
  String getType();

  List<String> importResource(List<PackageResource> resources, List<TerminologyServer> extServers);

  List<String> queryExistingResources(List<String> ids);
}
