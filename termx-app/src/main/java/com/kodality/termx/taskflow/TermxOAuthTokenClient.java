package com.kodality.termx.taskflow;

import com.kodality.commons.oauth.OAuthTokenClient;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import javax.inject.Singleton;

@Requires(property = "keycloak.sso-url")
@Singleton
public class TermxOAuthTokenClient extends OAuthTokenClient {
  public TermxOAuthTokenClient(@Value("${keycloak.sso-url}") String ssoUrl,
                               @Value("${keycloak.client-id}") String ssoClientId,
                               @Value("${keycloak.client-secret}") String ssoClientSecret) {
    super(ssoUrl, ssoClientId, ssoClientSecret);
  }
}
