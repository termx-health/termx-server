package com.kodality.termx.sys.server;

import com.kodality.commons.cache.CacheManager;
import com.kodality.commons.client.HttpClient;
import com.kodality.commons.oauth.OAuthTokenClient;
import com.kodality.termx.sys.server.TerminologyServer.TerminologyServerHeader;
import java.net.http.HttpRequest.Builder;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javax.inject.Singleton;

@Singleton
public class TerminologyServerHttpClientService {
  private final TerminologyServerService serverService;
  private final CacheManager cache = new CacheManager();

  public TerminologyServerHttpClientService(TerminologyServerService serverService) {
    this.serverService = serverService;
    cache.initCache("http-client", 25, 60);
  }

  public CompletableFuture<ServerHttpClientConfig> getConfig(Long serverId) {
    return cache.getCf("http-client", serverId.toString(), () -> {
      TerminologyServer server = serverService.load(serverId);
      String token = getAccessToken(server);
      ServerHttpClientConfig conf = new ServerHttpClientConfig(server.getRootUrl(), token, server.getHeaders());

      return CompletableFuture.completedFuture(conf);
    });
  }

  public CompletableFuture<HttpClient> getHttpClient(Long serverId) {
    return getConfig(serverId).thenApply(resp -> new HttpClient(resp.rootUrl()) {
      @Override
      public Builder builder(String path) {
        Builder b = super.builder(path);
        if (resp.accessToken() != null) {
          b.setHeader("Authorization", "Bearer " + resp.accessToken());
        }
        if (resp.headers() != null) {
          resp.headers().forEach(h -> b.header(h.getKey(), h.getValue()));
        }
        return b;
      }
    });
  }

  private static String getAccessToken(TerminologyServer server) {
    if (server.getAuthConfig() == null) {
      return null;
    }
    String url = server.getAuthConfig().getAccessTokenUrl();
    String clientId = server.getAuthConfig().getClientId();
    String clientSecret = server.getAuthConfig().getClientSecret();
    OAuthTokenClient client = new OAuthTokenClient(url, clientId, clientSecret);
    return client.getAccessToken();
  }

  public record ServerHttpClientConfig(String rootUrl, String accessToken, List<TerminologyServerHeader> headers) {}
}
