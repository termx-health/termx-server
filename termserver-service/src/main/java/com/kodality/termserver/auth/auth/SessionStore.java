package com.kodality.termserver.auth.auth;

import io.micronaut.http.context.ServerRequestContext;
import java.util.Optional;

public class SessionStore {
  public static final String KEY = "sessioninfo";

  private static ThreadLocal<SessionInfo> local = new ThreadLocal<>();

  public static Optional<SessionInfo> get() {
    if (local.get() != null) {
      return Optional.of(local.get());
    }
    return ServerRequestContext.currentRequest().flatMap(req -> req.getAttribute(KEY, SessionInfo.class));
  }

}
