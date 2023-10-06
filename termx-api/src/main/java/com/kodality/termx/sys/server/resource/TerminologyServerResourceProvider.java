package com.kodality.termx.sys.server.resource;

public interface TerminologyServerResourceProvider {
  String getType();

  Object getResource(Long serverId, String resourceId);
}
