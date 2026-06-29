package org.termx.core.sys.server.httpclient;

import com.kodality.commons.cache.CacheManager;
import com.kodality.commons.oauth.OAuthTokenClient;
import org.termx.sys.server.TerminologyServer;
import org.termx.sys.server.TerminologyServer.AuthType;
import org.termx.sys.server.TerminologyServer.TerminologyServerAuthConfig;
import org.termx.sys.server.TerminologyServer.TerminologyServerHeader;
import org.termx.core.sys.server.SecretEncryptor;
import org.termx.core.sys.server.TerminologyServerRepository;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.function.Supplier;
import org.apache.commons.lang3.StringUtils;

public abstract class ServerHttpClientService implements ServerHttpClientProvider {
  protected final TerminologyServerRepository serverService;
  protected final SecretEncryptor secretEncryptor;
  protected final CacheManager cache = new CacheManager();

  public ServerHttpClientService(TerminologyServerRepository serverService, SecretEncryptor secretEncryptor) {
    this.serverService = serverService;
    this.secretEncryptor = secretEncryptor;
    cache.initCache("http-client", 25, 600);
  }

  protected abstract ServerHttpClient buildHttpClient(ServerHttpClientConfig config);

  public ServerHttpClient getHttpClient(Long serverId) {
    return cache.get("http-client", serverId.toString(), () -> {
      TerminologyServer server = serverService.load(serverId);
      var config = new ServerHttpClientConfig(server.getRootUrl(), buildAuthorizationSupplier(server), server.getHeaders());
      return buildHttpClient(config);
    });
  }

  /**
   * Builds a supplier for the {@code Authorization} header value based on the server's {@link TerminologyServerAuthConfig}.
   * Returns {@code null} when no authentication is configured. The OAuth2 token client is instantiated once per cached
   * http-client so the bearer token is cached (with expiry) per server, not re-fetched on every request.
   */
  protected Supplier<String> buildAuthorizationSupplier(TerminologyServer server) {
    TerminologyServerAuthConfig auth = server.getAuthConfig();
    if (auth == null) {
      return null;
    }
    String clientSecret = secretEncryptor.decrypt(auth.getClientSecret());
    String authType = resolveAuthType(auth);
    switch (authType) {
      case AuthType.NONE:
        return null;
      case AuthType.BASIC: {
        String credentials = StringUtils.defaultString(auth.getClientId()) + ":" + StringUtils.defaultString(clientSecret);
        String header = "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        return () -> header;
      }
      case AuthType.APIKEY:
        // The api key is stored as the full Authorization header value (e.g. "ApiKey xxx" or "Bearer xxx").
        return StringUtils.isBlank(clientSecret) ? null : () -> clientSecret;
      case AuthType.OAUTH2:
      default: {
        OAuthTokenClient tokenClient = new ScopedOAuthTokenClient(auth.getAccessTokenUrl(), auth.getClientId(), clientSecret, auth.getScope());
        return () -> "Bearer " + tokenClient.getAccessToken();
      }
    }
  }

  /** Resolves the auth type, falling back to the legacy behaviour (oauth2 when client credentials are present). */
  private String resolveAuthType(TerminologyServerAuthConfig auth) {
    if (StringUtils.isNotBlank(auth.getAuthType())) {
      return auth.getAuthType();
    }
    boolean hasOauth = StringUtils.isNotBlank(auth.getAccessTokenUrl()) || StringUtils.isNotBlank(auth.getClientId()) || StringUtils.isNotBlank(auth.getClientSecret());
    return hasOauth ? AuthType.OAUTH2 : AuthType.NONE;
  }


  public void afterServerSave(Long serverId) {
    cache.remove("http-client", serverId.toString());
  }


  public interface ServerHttpClient {
  }

  public record ServerHttpClientConfig(String rootUrl, Supplier<String> authorizationHeader, List<TerminologyServerHeader> headers) {}
}
