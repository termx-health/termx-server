package com.kodality.termserver.auth.auth;

import com.auth0.jwt.JWT;
import com.auth0.jwt.exceptions.JWTDecodeException;
import com.auth0.jwt.exceptions.SignatureVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.kodality.commons.cache.CacheManager;
import com.kodality.commons.client.HttpClient;
import com.kodality.commons.client.HttpClientError;
import com.kodality.commons.util.JsonUtil;
import com.kodality.termserver.auth.SessionInfo;
import com.kodality.termserver.auth.SessionInfo.AuthenticationProvider;
import io.micronaut.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
@Singleton
public class SmartSessionProvider extends SessionProvider {
  private static final String AUTHORIZATION = "Authorization";
  private static final String BEARER = "Bearer";
  private static final String ISS_HEADER = "X-Smart-Iss";
  private static final List<String> ALLOWED_ISS = List.of("https://launch.smarthealthit.org/v/r2/fhir");
  private final CacheManager issCache = new CacheManager();
  private final Map<String, String> resourceTypes = Map.of(
      "CodeSystem", "CodeSystem",
      "ValueSet", "ValueSet"
  );
  private final Map<String, String> permissions = Map.of(
      "read", "view",
      "write", "edit",
      "*", "*"
  );

  public SmartSessionProvider() {
    issCache.initCache("iss", 100, 3600);
  }

  @Override
  public int getOrder() {
    return 49;
  }

  @Override
  public SessionInfo authenticate(HttpRequest<?> request) {
    String iss = request.getHeaders().get(ISS_HEADER);
    String auth = request.getHeaders().get(AUTHORIZATION);
    if (iss == null || !ALLOWED_ISS.contains(iss) || auth == null || !auth.startsWith(BEARER)) {
      return null;
    }
    return getSessionInfo(auth, iss);
  }

  private SessionInfo getSessionInfo(String auth, String iss) {
    try {
      String token = StringUtils.trim(StringUtils.substringAfter(auth, BEARER));
      DecodedJWT jwt = JWT.decode(token);

      if (jwt.getExpiresAt().before(new Date())) {
        return null;
      }
      if (!introspect(iss, token)) {
        return null;
      }

      String payload = new String(Base64.getUrlDecoder().decode(jwt.getPayload()));
      Map<String, Object> map = JsonUtil.toMap(payload);
      SessionInfo info = new SessionInfo();
      info.setToken(token);
      info.setPrivileges(buildPermissions(List.of(((String) map.get("scope")).split(" "))));
      info.setUsername("smart");
      info.setProvider(AuthenticationProvider.smart);
      info.setProviderProperties(Map.of("iss", iss));
      return info;
    } catch (SignatureVerificationException | JWTDecodeException e) {
      log.debug("", e);
      return null;
    }
  }

  private Set<String> buildPermissions(List<String> scopes) {
    return scopes.stream().map(scope -> {
      int s = scope.indexOf("/");
      int d = scope.indexOf(".");
      if (s <= 0 || d <= s || d == scope.length()) {
        return null;
      }
      String resourceType = scope.substring(s + 1, d);
      String permission = scope.substring(d + 1);

      return "*." + resourceTypes.get(resourceType) + "." + permissions.get(permission);
    }).filter(Objects::nonNull).collect(Collectors.toSet());
  }

  private Map<String, String> loadIssMeta(String iss) {
    return issCache.getCf("iss", "", () -> new HttpClient(iss).GET("metadata").thenApply(resp -> {
      List<Map> extensions = JsonUtil.read(resp.body(), "rest[0].security.extension[0].extension");
      Map<String, String> result = new HashMap<>();
      extensions.forEach(v -> result.put((String) v.get("url"), (String) v.get("valueUri")));
      return result;
    })).join();
  }

  private boolean introspect(String iss, String token) {
    try {
      String url = this.loadIssMeta(iss).get("introspect");
      HttpClient client = new HttpClient(url);
      client.execute(client.builder("").POST(BodyPublishers.ofString("token=" + token)).header("Content-Type", "application/x-www-form-urlencoded")
          .header("Authorization", "Bearer " + token).build());
      return true;
    } catch (HttpClientError e) {
      return false;
    }
  }
}
