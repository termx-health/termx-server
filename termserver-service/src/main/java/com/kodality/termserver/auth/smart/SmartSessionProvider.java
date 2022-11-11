package com.kodality.termserver.auth.smart;

import com.kodality.termserver.auth.auth.SessionInfo;
import com.kodality.termserver.auth.auth.SessionProvider;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import java.util.stream.Stream;
import javax.inject.Singleton;

@Singleton
public class SmartSessionProvider extends SessionProvider {

  @Override
  public int getOrder() {
    return 1;
  }

  @Override
  public SessionInfo authenticate(HttpRequest<?> request) {
    if (startsWith(request.getPath(), "/smart/launch")) {
      return new SessionInfo();
    }
    return null;
  }

  private boolean startsWith(String path, String prefix) {
    return path.equals(prefix) || path.startsWith(prefix + "/");
  }

}
