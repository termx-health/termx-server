package org.termx.core.sys.server.httpclient;

import org.termx.sys.server.ServerConnectionCheckResult;

public interface ServerHttpClientProvider {
  String getKind();

  void afterServerSave(Long serverId);

  /**
   * Performs a live connectivity check against the saved server, using the same root URL, headers and
   * authentication as real calls. Never throws — transport/HTTP failures are reported inside the result.
   */
  ServerConnectionCheckResult checkConnection(Long serverId);
}
