package com.kodality.termserver.auth.auth;

import com.auth0.jwk.Jwk;
import com.auth0.jwk.JwkException;
import com.auth0.jwk.UrlJwkProvider;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTDecodeException;
import com.auth0.jwt.exceptions.SignatureVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.kodality.commons.cache.CacheManager;
import com.kodality.commons.client.HttpClient;
import com.kodality.commons.util.JsonUtil;
import com.kodality.termserver.auth.SessionInfo;
import com.kodality.termserver.auth.SessionInfo.AuthenticationProvider;
import io.micronaut.context.annotation.Value;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.cookie.Cookie;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
@Singleton
public class OAuthSessionProvider extends SessionProvider {
  private static final String AUTHORIZATION = "Authorization";
  private static final String BEARER = "Bearer";
  public static final String OAUTH_TOKEN_COOKIE = "oauth-token";

  private final HttpClient jwksClient;
  private final CacheManager jwksCache = new CacheManager();
  private final PrivilegeStore privilegeStore;

  public OAuthSessionProvider(@Value("${auth.oauth.jwks-url}") String userinfoUrl, PrivilegeStore privilegeStore) {
    this.privilegeStore = privilegeStore;
    jwksCache.initCache("jwks", 1, 600);
    this.jwksClient = new HttpClient(userinfoUrl);
  }

  @Override
  public int getOrder() {
    return 30;
  }

  @Override
  public SessionInfo authenticate(HttpRequest<?> request) {
    return request.getHeaders()
        .getFirst(AUTHORIZATION)
        .filter(auth -> auth.startsWith(BEARER))
        .or(() -> request.getCookies().findCookie(OAUTH_TOKEN_COOKIE).map(Cookie::getValue))
        .map(this::getSessionInfo)
        .orElse(null);
  }

  private SessionInfo getSessionInfo(String auth) {
    try {
      String token = StringUtils.trim(StringUtils.substringAfter(auth, BEARER));
      DecodedJWT jwt = JWT.decode(token);
      Jwk jwk = getJwks().get(jwt.getKeyId());
      if (jwk == null) {
        return null;
      }
      Algorithm.RSA256((RSAPublicKey) jwk.getPublicKey(), null).verify(jwt);

      if (jwt.getExpiresAt().before(new Date())) {
        return null;
      }
      String payload = new String(Base64.getUrlDecoder().decode(jwt.getPayload()));
      Map<String, Object> map = JsonUtil.toMap(payload);
      SessionInfo info = new SessionInfo();
      info.setProvider(AuthenticationProvider.sso);
      info.setPrivileges(privilegeStore.getPrivileges( (List<String>) map.get("roles")));
      info.setUsername((String) map.get("preferred_username"));
      return info;
    } catch (SignatureVerificationException | JwkException | JWTDecodeException e) {
      log.debug("", e);
      return null;
    }
  }

  /**
   * @see UrlJwkProvider
   */
  private Map<String, Jwk> getJwks() {
    return jwksCache.getCf("jwks", "", () -> jwksClient.GET("").thenApply(resp -> {
      Map<String, Object> body = JsonUtil.toMap(resp.body());
      List<Map<String, Object>> keys = (List<Map<String, Object>>) body.get("keys");
      return keys.stream().map(Jwk::fromValues).collect(Collectors.toMap(Jwk::getId, k -> k));
    })).join();
  }
}
