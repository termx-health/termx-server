package com.kodality.termx.terminology;

import com.kodality.termx.sys.server.TerminologyServerHttpClientService;
import com.kodality.termx.sys.server.TerminologyServerService;
import java.net.http.HttpRequest.Builder;
import java.util.concurrent.CompletableFuture;
import javax.inject.Singleton;

@Singleton
public class TerminologyServerFhirClientService extends TerminologyServerHttpClientService {
  public TerminologyServerFhirClientService(TerminologyServerService serverService) {
    super(serverService);
  }

  public CompletableFuture<com.kodality.zmei.fhir.client.FhirClient> getFhirClient(Long serverId) {
    return getConfig(serverId).thenApply(resp -> new com.kodality.zmei.fhir.client.FhirClient(resp.rootUrl()) {
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
}
