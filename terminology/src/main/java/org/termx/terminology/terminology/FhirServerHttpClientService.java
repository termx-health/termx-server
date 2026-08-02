package org.termx.terminology.terminology;

import org.termx.sys.server.ServerConnectionCheckResult;
import org.termx.sys.server.TerminologyServer;
import org.termx.sys.server.TerminologyServerKind;
import org.termx.core.sys.server.SecretEncryptor;
import org.termx.core.sys.server.TerminologyServerRepository;
import org.termx.core.sys.server.httpclient.CapabilityStatementSummary;
import org.termx.core.sys.server.httpclient.ServerHttpClientService;
import com.kodality.zmei.fhir.client.FhirClientError;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.Builder;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletionException;
import jakarta.inject.Singleton;
import org.apache.commons.lang3.StringUtils;

@Singleton
public class FhirServerHttpClientService extends ServerHttpClientService {
  public FhirServerHttpClientService(TerminologyServerRepository serverService, SecretEncryptor secretEncryptor) {
    super(serverService, secretEncryptor);
  }

  @Override
  public String getKind() {
    return TerminologyServerKind.fhir;
  }

  @Override
  public ServerConnectionCheckResult checkConnection(Long serverId) {
    ServerConnectionCheckResult result = new ServerConnectionCheckResult();
    TerminologyServer server = serverService.load(serverId);
    if (server == null) {
      return result.setSuccess(false).setError("Server not found");
    }
    result.setUrl(StringUtils.stripEnd(server.getRootUrl(), "/") + "/metadata");
    FhirServerHttpClient client = getHttpClient(serverId);
    long start = System.currentTimeMillis();
    try {
      HttpRequest request = client.builder("metadata").header("Accept", "application/fhir+json").GET().build();
      HttpResponse<String> response = client.executeAsync(request).join();
      result.setStatusCode(response.statusCode());
      result.setSuccess(response.statusCode() >= 200 && response.statusCode() < 300);
      CapabilityStatementSummary.apply(response.body(), result);
    } catch (Exception e) {
      Throwable t = e instanceof CompletionException ? e.getCause() : e;
      while (t != null) {
        if (t instanceof FhirClientError err) {
          result.setStatusCode(err.getResponse().statusCode());
          result.setError(err.getMessage());
          break;
        }
        t = t.getCause();
      }
      if (result.getError() == null) {
        result.setError(e.getMessage());
      }
      result.setSuccess(false);
    } finally {
      result.setDurationMs(System.currentTimeMillis() - start);
    }
    return result;
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
