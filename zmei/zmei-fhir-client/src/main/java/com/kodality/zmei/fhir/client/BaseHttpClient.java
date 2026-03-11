package com.kodality.zmei.fhir.client;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.kodality.zmei.fhir.FhirMapper;
import com.kodality.zmei.fhir.client.FhirClientError.Response;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
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
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import lombok.AllArgsConstructor;

import static java.util.stream.Collectors.joining;

public class BaseHttpClient {
  protected final java.net.http.HttpClient httpClient;
  protected String baseUrl;

  protected BaseHttpClient() {
    this.httpClient = buildHttpClient();
  }

  protected BaseHttpClient(String baseUrl) {
    this();
    this.baseUrl = baseUrl != null && baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
  }

  protected HttpClient buildHttpClient() {
    return HttpClient.newBuilder().build();
  }

  public Builder builder(String path) {
    String url = baseUrl != null ? baseUrl + (path.startsWith("/") ? "" : "/") + path : path;
    Builder builder = HttpRequest.newBuilder();
    return builder.uri(URI.create(url));
  }

  public CompletableFuture<HttpResponse<String>> executeAsync(HttpRequest request) {
    return httpClient.sendAsync(request, new WrappedBodyHandler<>(request, BodyHandlers.ofString()))
        .exceptionally(ex -> {
          if (ex.getCause() instanceof FhirClientError) {
            throw (FhirClientError) ex.getCause();
          }
          throw new FhirClientException(ex);
        });
  }

  public static String toQueryParams(Object o) {
    return o == null ? "" : FhirMapper.getObjectMapper().convertValue(o, QueryParamBuilder.class).get();
  }

  protected String extractId(HttpResponse<String> r) {
    return r.headers().firstValue("Location").map(l -> {
      int historyIndex = l.indexOf("/_history");
      if (historyIndex != -1) {
        l = l.substring(0, historyIndex);
      }
      return l.substring(l.lastIndexOf("/") + 1);
    }).orElse(null);
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
        ((Collection<Object>) property).forEach(p -> add(name, p.toString()));
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
          throw new FhirClientError(request, response);
        });
      }
      return wrappedHandler.apply(responseInfo);
    }
  }

}
