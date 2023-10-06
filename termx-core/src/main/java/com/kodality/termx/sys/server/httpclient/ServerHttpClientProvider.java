package com.kodality.termx.sys.server.httpclient;

public interface ServerHttpClientProvider {
  String getKind();

  void afterServerSave(Long serverId);
}
