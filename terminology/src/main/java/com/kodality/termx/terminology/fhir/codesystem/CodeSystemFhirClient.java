package com.kodality.termx.terminology.fhir.codesystem;

import com.kodality.zmei.fhir.FhirMapper;
import com.kodality.zmei.fhir.client.FhirResourceClient;
import com.kodality.zmei.fhir.resource.other.Parameters;
import com.kodality.zmei.fhir.resource.terminology.CodeSystem;
import com.kodality.zmei.fhir.search.FhirQueryParams;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.util.concurrent.CompletableFuture;

public class CodeSystemFhirClient extends FhirResourceClient<CodeSystem> {

  public CodeSystemFhirClient(String url) {
    super(url + "/fhir/CodeSystem", CodeSystem.class);
  }

  public CompletableFuture<Parameters> lookup(FhirQueryParams params) {
    HttpRequest request = builder("/$lookup?" + toQueryParams(params)).GET().build();
    return executeAsync(request).thenApply(r -> FhirMapper.fromJson(r.body(), Parameters.class));
  }

  public CompletableFuture<Parameters> validateCode(FhirQueryParams params) {
    HttpRequest request = builder("/$validate-code?" + toQueryParams(params)).GET().build();
    return executeAsync(request).thenApply(r -> FhirMapper.fromJson(r.body(), Parameters.class));
  }

  public CompletableFuture<Parameters> subsumes(FhirQueryParams params) {
    HttpRequest request = builder("/$subsumes?" + toQueryParams(params)).GET().build();
    return executeAsync(request).thenApply(r -> FhirMapper.fromJson(r.body(), Parameters.class));
  }

  public CompletableFuture<Parameters> subsumes(Parameters params) {
    String json = FhirMapper.toJson(params);
    HttpRequest request = builder("/$subsumes").POST(BodyPublishers.ofString(json)).header("Content-Type", "application/json").build();
    return executeAsync(request).thenApply(r -> FhirMapper.fromJson(r.body(), Parameters.class));
  }

  public CompletableFuture<Parameters> findMatches(Parameters params) {
    String json = FhirMapper.toJson(params);
    HttpRequest request = builder("/$find-matches").POST(BodyPublishers.ofString(json)).header("Content-Type", "application/json").build();
    return executeAsync(request).thenApply(r -> FhirMapper.fromJson(r.body(), Parameters.class));
  }

  public CompletableFuture<Parameters> sync(Parameters params) {
    String json = FhirMapper.toJson(params);
    HttpRequest request = builder("/$sync").POST(BodyPublishers.ofString(json)).header("Content-Type", "application/json").build();
    return executeAsync(request).thenApply(r -> FhirMapper.fromJson(r.body(), Parameters.class));
  }

}
