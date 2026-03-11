package com.kodality.commons.oauth;

import com.kodality.commons.client.HttpClient;
import java.net.http.HttpRequest.Builder;

public class OAuthenticatedHttpClient extends HttpClient {
  private OAuthTokenClient oauthClient;

  public OAuthenticatedHttpClient(String baseUrl, OAuthTokenClient oauthClient) {
    super(baseUrl);
    this.oauthClient = oauthClient;
  }

  @Override
  public Builder builder(String path) {
    Builder builder = super.builder(path);
    builder.setHeader("Authorization", "Bearer " + oauthClient.getAccessToken());
    return builder;
  }

}
