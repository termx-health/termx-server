package com.kodality.termserver.auth.auth;

import com.kodality.commons.cache.CacheManager;
import com.kodality.commons.util.JsonUtil;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.cookie.Cookie;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton

public class OAuthSessionProvider extends SessionProvider{

  private static final String AUTHORIZATION = "Authorization";
  public static final String OAUTH_TOKEN_COOKIE = "oauth-token";

  private final CacheManager tokenCache = new CacheManager();
  private final Base64.Decoder decoder = Base64.getUrlDecoder();

  public OAuthSessionProvider() {
    tokenCache.initCache("tokens", 1000, 5);
  }

  @Override
  public int getOrder() {
    return 2;
  }

  @Override
  public SessionInfo authenticate(HttpRequest<?> request) {
    return request.getHeaders()
        .getFirst(AUTHORIZATION)
        .filter(auth -> auth.startsWith("Bearer "))
        .or(() -> request.getCookies().findCookie(OAUTH_TOKEN_COOKIE).map(Cookie::getValue))
        .map(this::getSessionInfo)
        .orElse(null);
  }

  private SessionInfo getSessionInfo(String auth) {
    return tokenCache.getCf("tokens", auth, () -> {
      log.debug("Querying user info from SSO with authorization header: {}", auth);
      String[] chunks = auth.split("\\.");
      String payload = new String(decoder.decode(chunks[1]));
      SessionInfo sessionInfo = JsonUtil.fromJson(payload, SessionInfo.class);
      return CompletableFuture.completedFuture(sessionInfo);
    }).join();
  }

}
