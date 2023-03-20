package com.kodality.termserver.auth.auth;

import com.kodality.termserver.auth.SessionInfo;
import io.micronaut.http.HttpRequest;

public abstract class SessionProvider {

  public abstract SessionInfo authenticate(HttpRequest<?> request);

  public int getOrder() {
    return 50;
  }

}

