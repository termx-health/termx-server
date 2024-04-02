package com.kodality.termx.sys.server.resource;

public interface TerminologyServerResourceSyncProvider {
  String getType();

  void sync(Long sourceServerId, Long targetServerId, String resourceId, boolean clearSync);
}
