package com.kodality.termserver.sys.server.resource;

public interface TerminologyServerResourceProvider {
  String getType();

  Object getResource(String serverRootUrl, String resourceId);
}
