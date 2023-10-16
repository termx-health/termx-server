package com.kodality.termx.core.sys.server.httpclient;

import com.kodality.commons.cache.CacheManager;
import com.kodality.commons.oauth.OAuthTokenClient;
import com.kodality.termx.sys.server.TerminologyServer;
import com.kodality.termx.sys.server.TerminologyServer.TerminologyServerHeader;
import com.kodality.termx.core.sys.server.TerminologyServerRepository;
import java.util.List;
import java.util.function.Supplier;

public abstract class ServerHttpClientService implements ServerHttpClientProvider {
  protected final TerminologyServerRepository serverService;
  protected final CacheManager cache = new CacheManager();

  public ServerHttpClientService(TerminologyServerRepository serverService) {
    this.serverService = serverService;
    cache.initCache("http-client", 25, 600);
  }

  protected abstract ServerHttpClient buildHttpClient(ServerHttpClientConfig config);

  public ServerHttpClient getHttpClient(Long serverId) {
    return cache.get("http-client", serverId.toString(), () -> {
      TerminologyServer server = serverService.load(serverId);

      Supplier<String> accessToken = null;
      if (server.getAuthConfig() != null) {
        String url = server.getAuthConfig().getAccessTokenUrl();
        String clientId = server.getAuthConfig().getClientId();
        String clientSecret = server.getAuthConfig().getClientSecret();
        accessToken = () -> new OAuthTokenClient(url, clientId, clientSecret).getAccessToken();
      }

      var config = new ServerHttpClientConfig(server.getRootUrl(), accessToken, server.getHeaders());
      return buildHttpClient(config);
    });
  }


  public void afterServerSave(Long serverId) {
    cache.remove("http-client", serverId.toString());
  }


  public interface ServerHttpClient {
  }

  public record ServerHttpClientConfig(String rootUrl, Supplier<String> accessToken, List<TerminologyServerHeader> headers) {}
}
