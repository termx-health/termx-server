package com.kodality.termx.auth.auth;

import com.kodality.termx.auth.SessionInfo;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import javax.inject.Singleton;

@Singleton
public class OptionsSessionProvider extends SessionProvider {

  @Override
  public int getOrder() {
    return 1;
  }

  @Override
  public SessionInfo authenticate(HttpRequest<?> request) {
    if (request.getMethod() == HttpMethod.OPTIONS) {
      return new SessionInfo();
    }
    return null;
  }

}
