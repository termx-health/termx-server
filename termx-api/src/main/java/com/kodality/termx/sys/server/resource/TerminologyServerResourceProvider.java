package com.kodality.termx.sys.server.resource;

public interface TerminologyServerResourceProvider {
  boolean checkType(String type);

  Object getResource(Long serverId, String resourceId);
}
