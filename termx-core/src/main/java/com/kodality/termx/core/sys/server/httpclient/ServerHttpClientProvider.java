package com.kodality.termx.core.sys.server.httpclient;

public interface ServerHttpClientProvider {
  String getKind();

  void afterServerSave(Long serverId);
}
