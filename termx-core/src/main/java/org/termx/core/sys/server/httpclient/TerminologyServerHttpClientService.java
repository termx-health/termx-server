package org.termx.core.sys.server.httpclient;

import com.kodality.commons.client.HttpClient;
import com.kodality.commons.client.HttpClientError;
import org.termx.sys.server.ServerConnectionCheckResult;
import org.termx.sys.server.TerminologyServer;
import org.termx.sys.server.TerminologyServerKind;
import org.termx.core.sys.server.SecretEncryptor;
import org.termx.core.sys.server.TerminologyServerRepository;
import java.net.http.HttpRequest.Builder;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletionException;
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

  @Override
  public ServerConnectionCheckResult checkConnection(Long serverId) {
    ServerConnectionCheckResult result = new ServerConnectionCheckResult();
    TerminologyServer server = serverService.load(serverId);
    if (server == null) {
      return result.setSuccess(false).setError("Server not found");
    }
    result.setUrl(server.getRootUrl());
    long start = System.currentTimeMillis();
    try {
      HttpResponse<String> response = getHttpClient(serverId).GET("").join();
      result.setStatusCode(response.statusCode());
      result.setSuccess(response.statusCode() >= 200 && response.statusCode() < 300);
      CapabilityStatementSummary.apply(response.body(), result);
    } catch (Exception e) {
      applyError(e, result);
    } finally {
      result.setDurationMs(System.currentTimeMillis() - start);
    }
    return result;
  }

  private static void applyError(Exception e, ServerConnectionCheckResult result) {
    Throwable t = e instanceof CompletionException ? e.getCause() : e;
    while (t != null) {
      if (t instanceof HttpClientError err) {
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
