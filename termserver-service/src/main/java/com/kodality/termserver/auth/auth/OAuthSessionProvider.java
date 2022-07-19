package com.kodality.termserver.auth.auth;

import com.kodality.commons.cache.CacheManager;
import com.kodality.commons.client.HttpClient;
import com.kodality.commons.util.JsonUtil;
import io.micronaut.context.annotation.Value;
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

  private final HttpClient authClient;
  private final CacheManager tokenCache = new CacheManager();

  public OAuthSessionProvider(@Value("${auth.oauth.userinfo}") String userinfoUrl) {
    tokenCache.initCache("tokens", 1000, 5);
    this.authClient = new HttpClient(userinfoUrl);
  }

  @Override
  public int getOrder() {
    return 50;
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
      java.net.http.HttpRequest req = authClient.builder("/").GET().headers(AUTHORIZATION, auth).build();
      return authClient.executeAsync(req)
          .thenApply(resp -> JsonUtil.fromJson(resp.body(), SessionInfo.class))
          .exceptionally(e -> {
            log.warn("Failed to get SessionInfo from SSO with authorization header: {}. Exception: {}", auth, e.getMessage());
            return null;
          });
    }).join();
  }

}
