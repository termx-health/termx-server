package com.kodality.termserver.auth.auth;

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
      return new SessionInfo().setUsername("test");
    }
    return null;
  }

}
