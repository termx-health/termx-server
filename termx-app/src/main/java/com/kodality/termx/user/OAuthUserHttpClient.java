package com.kodality.termx.user;

import com.kodality.commons.oauth.OAuthenticatedHttpClient;
import com.kodality.commons.util.JsonUtil;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Requires(property = "keycloak.url")
@Singleton
@RequiredArgsConstructor
public class OAuthUserHttpClient {
  private final OAuthenticatedHttpClient httpClient;

  public OAuthUserHttpClient(@Value("${keycloak.url}") String url, TermxOAuthTokenClient tokenClient) {
    httpClient = new OAuthenticatedHttpClient(url, tokenClient);
  }

  public CompletableFuture<List<OAuthUser>> getUsers() {
    return httpClient.GET("/users", JsonUtil.getListType(OAuthUser.class));
  }
}
