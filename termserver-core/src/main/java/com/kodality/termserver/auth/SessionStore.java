package com.kodality.termserver.auth;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.context.ServerRequestContext;
import java.util.Optional;

public class SessionStore {
  public static final String KEY = "session-info";

  private static final ThreadLocal<SessionInfo> LOCAL_SESSION = new ThreadLocal<>();

  public static Optional<SessionInfo> get() {
    if (LOCAL_SESSION.get() != null) {
      return Optional.of(LOCAL_SESSION.get());
    }
    return ServerRequestContext.currentRequest().flatMap(req -> req.getAttribute(KEY, SessionInfo.class));
  }

  public static Optional<SessionInfo> get(HttpRequest httpRequest) {
    return httpRequest.getAttribute(KEY, SessionInfo.class);
  }

  public static SessionInfo require() {
    return get().orElseThrow(() -> new IllegalStateException("No session found in request context"));
  }

  public static Runnable wrap(Runnable runnable) {
    HttpRequest<Object> req = ServerRequestContext.currentRequest().orElse(null);
    if (req != null) {
      return () -> ServerRequestContext.with(req, runnable);
    }
    return runnable;
  }

  public static void setLocal(SessionInfo sessionInfo) {
    LOCAL_SESSION.set(sessionInfo);
  }

  public static void clearLocal() {
    LOCAL_SESSION.remove();
  }
}
