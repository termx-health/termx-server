package com.kodality.commons.oauth;

import com.kodality.commons.exception.ApiException;
import com.kodality.commons.model.Issue;
import com.kodality.commons.util.JsonUtil;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpRequest.Builder;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OAuthTokenClient {
  private final static Integer CONNECT_TIMEOUT = 10_000;
  private final static Integer READ_TIMEOUT = 30_000;
  private final static Integer RENEWAL_PRECEDE = 30_000;
  private final String ssoUrl;
  private final String ssoClient;
  private final String ssoClientSecret;
  private final java.net.http.HttpClient authClient;

  private CompletableFuture<TokenData> token;

  public OAuthTokenClient(String ssoUrl, String ssoClient, String ssoClientSecret) {
    this.ssoUrl = ssoUrl;
    this.ssoClient = ssoClient;
    this.ssoClientSecret = ssoClientSecret;
    this.authClient = java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofMillis(CONNECT_TIMEOUT)).build();
  }

  public void invalidate() {
    this.token = null;
  }

  public String getAccessToken() {
    if (token == null || token.isCancelled() || token.isCompletedExceptionally()) {
      token = obtainToken();
      token.thenAccept(t -> token = CompletableFuture.completedFuture(t));
    }
    TokenData tokenData = token.copy().join();
    if (tokenData.isExpired()) {
      token = null;
      return getAccessToken();
    }
    return tokenData.accessToken;
  }

  private CompletableFuture<TokenData> obtainToken() {
    String formParams = getFormParams().entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining("&"));

    Builder builder = HttpRequest
        .newBuilder(URI.create(ssoUrl + "/token"))
        .header("Content-Type", "application/x-www-form-urlencoded")
        .POST(BodyPublishers.ofString(formParams))
        .timeout(Duration.ofMillis(READ_TIMEOUT));
    return authClient.sendAsync(builder.build(), BodyHandlers.ofString()).thenApply(response -> {
      if (response.statusCode() != 200) {
        log.error("Got " + response.statusCode() + " while requesting token from " + response.uri().toString() + ", body: " + response.body());
        throw new ApiException(500, Issue.error("could not get token for '" + ssoClient + "'"));
      }
      Map<String, Object> json = JsonUtil.toMap(response.body());
      return new TokenData((String) json.get("access_token"), System.currentTimeMillis() + ((Long) json.get("expires_in") * 1000)
      );
    });
  }

  protected Map<String, String> getFormParams() {
    return Map.of(
        "client_id", ssoClient,
        "client_secret", ssoClientSecret,
        "grant_type", "client_credentials"
    );
  }

  private record TokenData(String accessToken, long expirationTime) {
    public boolean isExpired() {
      return expirationTime - RENEWAL_PRECEDE < System.currentTimeMillis();
    }
  }

}
