package com.kodality.termx.core.sys.server.httpclient;

import com.kodality.commons.client.HttpClient;
import com.kodality.termx.sys.server.TerminologyServerKind;
import com.kodality.termx.core.sys.server.TerminologyServerRepository;
import java.net.http.HttpRequest.Builder;
import jakarta.inject.Singleton;

@Singleton
public class TerminologyServerHttpClientService extends ServerHttpClientService {
  public TerminologyServerHttpClientService(TerminologyServerRepository serverService) {
    super(serverService);
  }

  @Override
  public String getKind() {
    return TerminologyServerKind.terminology;
  }

  protected TerminologyServerHttpClient buildHttpClient(ServerHttpClientConfig config) {
    return new TerminologyServerHttpClient(config);
  }

  @Override
  public TerminologyServerHttpClient getHttpClient(Long serverId) {
    return (TerminologyServerHttpClient) super.getHttpClient(serverId);
  }


  public static class TerminologyServerHttpClient extends HttpClient implements ServerHttpClient {
    private final ServerHttpClientConfig config;

    public TerminologyServerHttpClient(ServerHttpClientConfig config) {
      super(config.rootUrl());
      this.config = config;
    }

    @Override
    public Builder builder(String path) {
      Builder b = super.builder(path);
      if (config.accessToken() != null) {
        b.setHeader("Authorization", "Bearer " + config.accessToken().get());
      }
      if (config.headers() != null) {
        config.headers().forEach(h -> b.header(h.getKey(), h.getValue()));
      }
      return b;
    }
  }
}
