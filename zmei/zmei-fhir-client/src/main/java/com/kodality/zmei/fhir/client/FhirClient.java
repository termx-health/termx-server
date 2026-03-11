package com.kodality.zmei.fhir.client;

import com.kodality.zmei.fhir.FhirMapper;
import com.kodality.zmei.fhir.resource.DomainResource;
import com.kodality.zmei.fhir.resource.Resource;
import com.kodality.zmei.fhir.resource.other.Bundle;
import com.kodality.zmei.fhir.search.FhirQueryParams;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class FhirClient extends BaseHttpClient {

  public FhirClient(String baseUrl) {
    super(baseUrl);
  }

  private <R extends Resource> R fromJson(HttpResponse<String> r) {
    return (R) FhirMapper.fromJson(r.body(), Resource.class);
  }

  public <R extends Resource> CompletableFuture<R> read(String resourceType, String id) {
    HttpRequest request = builder(resourceType + "/" + id).GET().build();
    return executeAsync(request).thenApply(this::fromJson);
  }

  public CompletableFuture<HttpResponse<String>> delete(String resourceType, String id) {
    HttpRequest request = builder(resourceType + "/" + id).DELETE().build();
    return executeAsync(request);
  }

  public CompletableFuture<Bundle> search(String resourceType, FhirQueryParams params) {
    String query = toQueryParams(params);
    HttpRequest request = builder(resourceType + "?" + query).GET().build();
    return executeAsync(request).thenApply(this::fromJson);
  }

  public CompletableFuture<String> create(Resource resource) {
    String json = FhirMapper.toJson(resource);
    HttpRequest request = builder(resource.getResourceType()).POST(BodyPublishers.ofString(json)).header("Content-Type", "application/json").build();
    return executeAsync(request).thenApply(this::extractId);
  }

  public CompletableFuture<Bundle> transaction(Bundle bundle) {
    String json = FhirMapper.toJson(bundle);
    HttpRequest request = builder("").POST(BodyPublishers.ofString(json)).header("Content-Type", "application/json").build();
    return executeAsync(request).thenApply(this::fromJson);
  }

  public CompletableFuture<String> update(String id, Resource resource) {
    String json = FhirMapper.toJson(resource);
    HttpRequest request = builder(resource.getResourceType() + "/" + id).PUT(BodyPublishers.ofString(json)).header("Content-Type", "application/json").build();
    return executeAsync(request).thenApply(r -> id);
  }

}
