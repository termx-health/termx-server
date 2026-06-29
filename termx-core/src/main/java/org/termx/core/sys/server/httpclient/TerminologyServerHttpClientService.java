package org.termx.core.sys.server.httpclient;

import com.kodality.commons.client.HttpClient;
import org.termx.sys.server.TerminologyServerKind;
import org.termx.core.sys.server.SecretEncryptor;
import org.termx.core.sys.server.TerminologyServerRepository;
import java.net.http.HttpRequest.Builder;
import jakarta.inject.Singleton;

@Singleton
public class TerminologyServerHttpClientService extends ServerHttpClientService {
  public TerminologyServerHttpClientService(TerminologyServerRepository serverService, SecretEncryptor secretEncryptor) {
    super(serverService, secretEncryptor);
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
      if (config.authorizationHeader() != null) {
        b.setHeader("Authorization", config.authorizationHeader().get());
      }
      if (config.headers() != null) {
        config.headers().forEach(h -> b.header(h.getKey(), h.getValue()));
      }
      return b;
    }
  }
}
