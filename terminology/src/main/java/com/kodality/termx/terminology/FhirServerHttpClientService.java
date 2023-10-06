package com.kodality.termx.terminology;

import com.kodality.termx.sys.server.TerminologyServerKind;
import com.kodality.termx.sys.server.TerminologyServerRepository;
import com.kodality.termx.sys.server.httpclient.ServerHttpClientService;
import java.net.http.HttpRequest.Builder;
import javax.inject.Singleton;

@Singleton
public class FhirServerHttpClientService extends ServerHttpClientService {
  public FhirServerHttpClientService(TerminologyServerRepository serverService) {
    super(serverService);
  }

  @Override
  public String getKind() {
    return TerminologyServerKind.fhir;
  }

  protected FhirServerHttpClient buildHttpClient(ServerHttpClientConfig config) {
    return new FhirServerHttpClient(config);
  }

  @Override
  public FhirServerHttpClient getHttpClient(Long serverId) {
    return (FhirServerHttpClient) super.getHttpClient(serverId);
  }


  public static class FhirServerHttpClient extends com.kodality.zmei.fhir.client.FhirClient implements ServerHttpClient {
    private final ServerHttpClientConfig config;

    public FhirServerHttpClient(ServerHttpClientConfig config) {
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
