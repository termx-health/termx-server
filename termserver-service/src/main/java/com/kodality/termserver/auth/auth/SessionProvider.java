package com.kodality.termserver.auth.auth;

import io.micronaut.http.HttpRequest;

public abstract class SessionProvider {

  public abstract SessionInfo authenticate(HttpRequest<?> request);

  public int getOrder() {
    return 50;
  }

}

