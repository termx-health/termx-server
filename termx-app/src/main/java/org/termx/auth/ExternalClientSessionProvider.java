package org.termx.auth;

import io.micronaut.http.HttpRequest;
import jakarta.inject.Singleton;
import java.util.HashSet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.termx.auth.externalclient.ExternalClient;
import org.termx.auth.externalclient.ExternalClientService;
import org.termx.core.auth.SessionInfo;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class ExternalClientSessionProvider extends SessionProvider {
  private static final String AUTHORIZATION = "Authorization";
  private static final String BASIC = "Basic";
  private final ExternalClientService externalClientService;

  @Override
  public int getOrder() {
    return 30;
  }

  @Override
  public SessionInfo authenticate(HttpRequest<?> request) {
    return request.getHeaders()
        .getFirst(AUTHORIZATION)
        .filter(auth -> auth.startsWith(BASIC))
        .map(this::getSessionInfo)
        .orElse(null);
  }

  private SessionInfo getSessionInfo(String auth) {
    String basic = StringUtils.trim(StringUtils.substringAfter(auth, BASIC));
    ExternalClient client = externalClientService.find(basic);
    if (client == null) {
      return null;
    }
    SessionInfo s = new SessionInfo();
    s.setPrivileges(new HashSet<>(client.getPrivileges()));
    s.setUsername(client.getName());
    return s;
  }
}
