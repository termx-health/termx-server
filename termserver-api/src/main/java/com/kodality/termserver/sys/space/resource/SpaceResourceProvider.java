package com.kodality.termserver.sys.space.resource;

import com.kodality.termserver.sys.server.TerminologyServer;
import com.kodality.termserver.sys.spacepackage.PackageVersion.PackageResource;
import java.util.List;

public interface SpaceResourceProvider {
  String getType();

  List<String> importResource(List<PackageResource> resources, List<TerminologyServer> extServers);

  List<String> queryExistingResources(List<String> ids);
}
