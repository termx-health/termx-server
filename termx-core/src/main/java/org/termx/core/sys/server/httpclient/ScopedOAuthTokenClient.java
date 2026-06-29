package org.termx.core.sys.server.httpclient;

import com.kodality.commons.oauth.OAuthTokenClient;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;

/**
 * OAuth2 client-credentials token client that additionally sends a {@code scope} parameter.
 * Mirrors {@link OAuthTokenClient}'s token caching/expiry; only the request body differs.
 */
public class ScopedOAuthTokenClient extends OAuthTokenClient {
  private final String clientId;
  private final String clientSecret;
  private final String scope;

  public ScopedOAuthTokenClient(String ssoUrl, String clientId, String clientSecret, String scope) {
    super(ssoUrl, clientId, clientSecret);
    this.clientId = clientId;
    this.clientSecret = clientSecret;
    this.scope = scope;
  }

  @Override
  protected Map<String, String> getFormParams() {
    Map<String, String> params = new LinkedHashMap<>();
    params.put("client_id", clientId);
    params.put("client_secret", clientSecret);
    params.put("grant_type", "client_credentials");
    if (StringUtils.isNotBlank(scope)) {
      params.put("scope", URLEncoder.encode(scope, StandardCharsets.UTF_8));
    }
    return params;
  }
}
