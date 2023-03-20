package com.kodality.termserver.auth;


import io.micronaut.http.HttpRequest;
import java.util.Optional;

public interface CommonSessionProvider {
  Optional<CommonSessionInfo> get();
  Optional<CommonSessionInfo> get(HttpRequest httpRequest);

  void checkPermitted(String resourceId, String resourceType, String action);

  Runnable wrap(Runnable runnable);
  void setLocal(CommonSessionInfo sessionInfo);
  void clearLocal();
}
