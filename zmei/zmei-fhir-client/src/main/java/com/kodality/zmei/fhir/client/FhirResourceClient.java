package com.kodality.zmei.fhir.client;

import com.kodality.zmei.fhir.FhirMapper;
import com.kodality.zmei.fhir.resource.DomainResource;
import com.kodality.zmei.fhir.resource.other.Bundle;
import com.kodality.zmei.fhir.search.FhirQueryParams;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

public class FhirResourceClient<R extends DomainResource> extends BaseHttpClient {
  private final Class<R> resourceClass;

  public FhirResourceClient(String baseUrl, Class<R> resourceClass) {
    super(baseUrl);
    this.resourceClass = resourceClass;
  }

  private R fromJson(HttpResponse<String> r) {
    return FhirMapper.fromJson(r.body(), this.resourceClass);
  }

  private Bundle fromBundleJson(HttpResponse<String> r) {
    return FhirMapper.fromJson(r.body(), Bundle.class);
  }

  public CompletableFuture<R> read(String id) {
    HttpRequest request = builder(id).GET().build();
    return executeAsync(request).thenApply(this::fromJson);
  }

  public CompletableFuture<HttpResponse<String>> delete(String id) {
    HttpRequest request = builder(id).DELETE().build();
    return executeAsync(request);
  }

  public CompletableFuture<Bundle> search(FhirQueryParams params) {
    String query = "?" + toQueryParams(params);
    HttpRequest request = builder(query).GET().build();
    return executeAsync(request).thenApply(this::fromBundleJson);
  }

  public CompletableFuture<String> create(R resource) {
    String json = FhirMapper.toJson(resource);
    HttpRequest request = builder("").POST(BodyPublishers.ofString(json)).header("Content-Type", "application/json").build();
    return executeAsync(request).thenApply(this::extractId);
  }

  public CompletableFuture<String> update(String id, R resource) {
    String json = FhirMapper.toJson(resource);
    HttpRequest request = builder(id).PUT(BodyPublishers.ofString(json)).header("Content-Type", "application/json").build();
    return executeAsync(request).thenApply(r -> id);
  }

}
