package com.kodality.termx.sys.server.resource;

public interface TerminologyServerResourceSyncProvider {
  String getType();

  void syncFrom(String serverRootUrl, String resourceId);
}