package com.kodality.termserver.integration.github;

import com.fasterxml.jackson.core.type.TypeReference;
import com.kodality.commons.cache.CacheManager;
import com.kodality.commons.util.JsonUtil;
import com.kodality.termserver.auth.auth.SessionInfo;
import com.kodality.termserver.auth.auth.SessionStore;
import com.kodality.termserver.integration.github.GithubController.ExportData;
import io.micronaut.context.annotation.Value;
import io.micronaut.http.HttpHeaders;
import io.netty.handler.codec.http.QueryStringDecoder;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpRequest.Builder;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Singleton
public class GithubService {

  public static final String GITHUB_OAUTH = "https://github.com/login/oauth";
  public static final String GITHUB_USER_API = "https://api.github.com/user";
  private final HttpClient http;
  private final CacheManager cacheManager;

  @Value("${github.client.id}")
  private String clientId;

  @Value("${github.client.secret}")
  private String clientSecret;

  @Value("${github.app-name}")
  private String appName;

  public GithubService() {
    // Github oauth token lives 8 hours, we expire cache after 7
    // Token may be refreshed instead of expiring in the future
    // Cache is not distributed - if multiple instances of server running, Github integration may not work properly
    this.cacheManager = new CacheManager();
    this.cacheManager.initCache("user-token", 1000, TimeUnit.HOURS.toSeconds(7));
    this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
  }

  public String getAuthRedirect(String stateUrl) {
    String username = SessionStore.require().getUsername();
    String userPart = stateUrl.contains("?") ? "&username=" + username : "?username=" + username;
    String stateEncoded = URLEncoder.encode(stateUrl + userPart, StandardCharsets.UTF_8);
    return GITHUB_OAUTH + "/authorize?client_id=" + clientId + "&state=" + stateEncoded;
  }

  public String authorizeUser(String state, String code) {
    String token = getAccessToken(code);
    String user = new QueryStringDecoder(URLDecoder.decode(state, StandardCharsets.UTF_8)).parameters().get("username").get(0);
    this.cacheManager.getCache("user-token").put(user, token);
    if (isAppInstalled(user)) {
      return state;
    }
    return "https://github.com/apps/" + this.appName + "/installations/new?state=" + state;
  }

  private String getAccessToken(String code) {
    Map<String, String> payload = new HashMap<>();
    payload.put("client_id", clientId);
    payload.put("client_secret", clientSecret);
    payload.put("code", code);
    String body = JsonUtil.toJson(payload);

    URI uri;
    try {
      uri = new URI(GITHUB_OAUTH + "/access_token");
    } catch (URISyntaxException e) {
      throw new RuntimeException(e);
    }

    HttpRequest request = HttpRequest.newBuilder()
        .setHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
        .setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
        .uri(uri)
        .POST(BodyPublishers.ofString(body))
        .build();
    HttpResponse<String> response = send(request);
    Map<String, String> map = JsonUtil.fromJson(response.body(), new TypeReference<>() {});
    return map.get("access_token");
  }

  private boolean isAppInstalled(String user) {
    try {
      SessionStore.setLocal(new SessionInfo().setUsername(user));
      String json = getInstallations();
      Map map = JsonUtil.fromJson(json, Map.class);
      if (map.isEmpty() || !map.containsKey("installations")) {
        return false;
      }
      List installations = (List) map.get("installations");
      if (installations.isEmpty()) {
        return false;
      }
      return true;
    } finally {
      SessionStore.clearLocal();
    }
  }

  public String getInstallations() {
    String token = getCurrentUserToken();
    if (token == null) {
      return "{}";
    }
    return get(GITHUB_USER_API + "/installations").body();
  }

  public String getRepositories(String installationId) {
    return get(GITHUB_USER_API + "/installations/" + installationId + "/repositories").body();
  }

  public String export(ExportData data) {
    String sha = getExistingFileSha(data);

    Map<String, String> payload = new HashMap<>();
    payload.put("message", data.getMessage());
    payload.put("content", data.getContent());
    if (sha != null) {
      payload.put("sha", sha);
    }
    String uri = data.getRepoUrl() + "/contents/" + data.getPath();
    String body = JsonUtil.toJson(payload);
    return put(uri, body).body();
  }

  private String getExistingFileSha(ExportData data) {
    String uri = data.getRepoUrl() + "/contents/" + data.getPath();
    HttpResponse<String> response = get(uri);
    if (response.statusCode() == 200) {
      return (String) JsonUtil.fromJson(response.body(), Map.class).get("sha");
    }
    return null;
  }

  private HttpResponse<String> put(String uri, String body) {
    return send(buildBaseRequest(uri)
        .PUT(BodyPublishers.ofString(body))
        .build());
  }

  private HttpResponse<String> get(String uri) {
    return send(buildBaseRequest(uri).GET().build());
  }

  private Builder buildBaseRequest(String uri) {
    String token = getCurrentUserToken();
    if (token == null) {
      throw new IllegalAccessError("Token is missing from cache");
    }
    try {
      return HttpRequest.newBuilder()
          .setHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
          .setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
          .setHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token)
          .uri(new URI(uri));
    } catch (URISyntaxException e) {
      throw new RuntimeException(e);
    }
  }

  private HttpResponse<String> send(HttpRequest request) {
    try {
      return this.http.send(request, BodyHandlers.ofString());
    } catch (IOException | InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  private String getCurrentUserToken() {
    return (String) this.cacheManager.getCache("user-token").get(SessionStore.require().getUsername());
  }
}
