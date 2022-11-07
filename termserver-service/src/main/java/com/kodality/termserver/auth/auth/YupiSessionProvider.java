package com.kodality.termserver.auth.auth;

import com.kodality.commons.util.JsonUtil;
import com.kodality.termserver.auth.auth.SessionInfo.AuthenticationProvider;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpRequest;
import java.security.AuthProvider;
import java.util.List;
import java.util.Set;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Requires(property = "auth.dev.allowed", value = StringUtils.TRUE)
@Slf4j
@Singleton
public class YupiSessionProvider extends SessionProvider {
  private static final String BEARER_YUPI = "Bearer yupi";

  @Override
  public int getOrder() {
    return 40;
  }

  @Override
  public SessionInfo authenticate(HttpRequest<?> request) {
    if (request.getHeaders().getFirst("Authorization").map(auth -> auth.startsWith(BEARER_YUPI)).orElse(false)) {
      String sessionInfo = request.getHeaders().getFirst("Authorization").get().substring(BEARER_YUPI.length());
      if (!sessionInfo.isEmpty()) {
        return JsonUtil.fromJson(sessionInfo, SessionInfo.class);
      }
      return yupiDroopy();
    }
    return null;
  }

  private SessionInfo yupiDroopy() {
    SessionInfo s = new SessionInfo();
    s.setProvider(AuthenticationProvider.sso);
    s.setUsername("yupi");
    s.setLang("en");
    s.setPrivileges(Set.of("*.*.edit", "*.*.view", "*.*.publish"));
    return s;
  }
}
