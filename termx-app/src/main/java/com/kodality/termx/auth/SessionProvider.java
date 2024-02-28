package com.kodality.termx.auth;

import com.kodality.termx.core.auth.SessionInfo;
import io.micronaut.http.HttpRequest;

public abstract class SessionProvider {

  public abstract SessionInfo authenticate(HttpRequest<?> request);

  public int getOrder() {
    return 50;
  }

}

