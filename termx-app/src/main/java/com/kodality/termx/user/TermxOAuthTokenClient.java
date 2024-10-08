package com.kodality.termx.user;

import com.kodality.commons.oauth.OAuthTokenClient;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;

@Requires(property = "keycloak.sso-url")
@Singleton
public class TermxOAuthTokenClient extends OAuthTokenClient {
  public TermxOAuthTokenClient(@Value("${keycloak.sso-url}") String ssoUrl,
                               @Value("${keycloak.client-id}") String ssoClientId,
                               @Value("${keycloak.client-secret}") String ssoClientSecret) {
    super(ssoUrl, ssoClientId, ssoClientSecret);
  }
}
