package com.kodality.termserver.sys.server.resource;

public interface TerminologyServerResourceSyncProvider {
  String getType();

  void syncFrom(String serverRootUrl, String resourceId);
}
