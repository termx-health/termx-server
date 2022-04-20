package com.kodality.termserver.commons.client;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.kodality.termserver.commons.client.HttpClientError.Response;
import com.kodality.termserver.commons.util.JsonUtil;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.Builder;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
import java.net.http.HttpResponse.BodyHandlers;
import java.net.http.HttpResponse.BodySubscriber;
import java.net.http.HttpResponse.BodySubscribers;
import java.net.http.HttpResponse.ResponseInfo;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.StringUtils;

public class BaseHttpClient {
  private final java.net.http.HttpClient httpClient;
  private String baseUrl;

  public BaseHttpClient() {
    httpClient = java.net.http.HttpClient.newBuilder().build();
  }

  public BaseHttpClient(String baseUrl) {
    this();
    this.baseUrl = StringUtils.removeEnd(baseUrl, "/");
  }

  public Builder builder(String path) {
    String url = baseUrl != null ? baseUrl + (path == null ? "" : "/" + StringUtils.removeStart(path, "/")) : path;
    return HttpRequest.newBuilder()
        .version(Version.HTTP_1_1) // setting explicitly version 1.1 to overcome problem with long headers in HTTP2, as it seems java client cannot handle them properly
        .uri(URI.create(url));
  }

  public CompletableFuture<HttpResponse<String>> executeAsync(HttpRequest request) {
    return executeAsync(request, BodyHandlers.ofString());
  }

  public HttpResponse<String> execute(HttpRequest request) {
    return execute(request, BodyHandlers.ofString());
  }

  public <T> HttpResponse<T> execute(HttpRequest request, BodyHandler<T> bodyHandler) {
    try {
      return executeAsync(request, bodyHandler).join();
    } catch (CompletionException e) {
      if (e.getCause() != null && e.getCause() instanceof RuntimeException) {
        throw (RuntimeException) e.getCause();
      }
      throw e;
    }
  }

  protected <T> CompletableFuture<HttpResponse<T>> executeAsync(HttpRequest request, BodyHandler<T> bodyHandler) {
    return httpClient.sendAsync(request, new WrappedBodyHandler<>(request, bodyHandler)).exceptionally(e -> {
      if (e.getCause() instanceof HttpClientError) {
        throw (HttpClientError) e.getCause();
      } else {
        throw new HttpClientException("Failed to process request to " + request.uri().toString(), e);
      }
    });

  }

  public static String toQueryParams(Object o) {
    if (o == null) {
      return "";
    }
    return JsonUtil.getObjectMapper().convertValue(o, QueryParamBuilder.class).get();
  }

  public static class QueryParamBuilder {
    private final List<String> params = new ArrayList<>();

    @SuppressWarnings("unchecked")
    @JsonAnySetter
    public void add(String name, Object property) {
      if (property == null) {
        return;
      }
      if (property instanceof Collection) {
        ((Collection<Object>) property).forEach(p -> params.add(encode(name) + "=" + encode(p.toString())));
        return;
      }
      params.add(encode(name) + "=" + encode(property.toString()));
    }

    private static String encode(String val) {
      return URLEncoder.encode(val, StandardCharsets.UTF_8);
    }

    public String get() {
      return String.join("&", params);
    }

  }

  @AllArgsConstructor
  private static class WrappedBodyHandler<T> implements BodyHandler<T> {
    private final HttpRequest request;
    private final BodyHandler<T> wrappedHandler;

    @Override
    public BodySubscriber<T> apply(ResponseInfo responseInfo) {
      if (responseInfo.statusCode() > 299) {
        return BodySubscribers.mapping(BodySubscribers.ofString(StandardCharsets.UTF_8), json -> {
          Response response = new Response(responseInfo.statusCode(), responseInfo.headers(), json);
          throw new HttpClientError(request, response);
        });
      } else {
        return wrappedHandler.apply(responseInfo);
      }
    }
  }

}
