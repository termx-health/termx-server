package com.kodality.termx.sys.server.resource;

public interface TerminologyServerResourceSyncProvider {
  boolean checkType(String type);

  void sync(Long sourceServerId, Long targetServerId, String resourceId, boolean clearSync);
}
