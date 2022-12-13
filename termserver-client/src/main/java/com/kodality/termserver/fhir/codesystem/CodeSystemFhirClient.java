package com.kodality.termserver.fhir.codesystem;

import com.kodality.commons.client.BaseHttpClient;
import com.kodality.commons.client.HttpClient;
import com.kodality.zmei.fhir.resource.infrastructure.Parameters;
import com.kodality.zmei.fhir.resource.other.Bundle;
import com.kodality.zmei.fhir.resource.terminology.CodeSystem;
import com.kodality.zmei.fhir.search.FhirQueryParams;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class CodeSystemFhirClient {
  protected HttpClient client;

  public CodeSystemFhirClient(){}

  public CodeSystemFhirClient(String url, Function<String, HttpClient> clientProvider) {
    client = clientProvider.apply(url + "/fhir/CodeSystem");
  }

  public CompletableFuture<Bundle> search(FhirQueryParams params) {
    return client.GET("?" + BaseHttpClient.toQueryParams(params), Bundle.class);
  }

  public CompletableFuture<CodeSystem> save(FhirQueryParams params, CodeSystem codeSystem) {
    return client.PUT("?" + BaseHttpClient.toQueryParams(params), codeSystem, CodeSystem.class);
  }

  public CompletableFuture<Parameters> lookup(FhirQueryParams params) {
    return client.GET("/$lookup?" + BaseHttpClient.toQueryParams(params), Parameters.class);
  }

  public CompletableFuture<Parameters> validateCode(FhirQueryParams params) {
    return client.GET("/$validate-code?" + BaseHttpClient.toQueryParams(params), Parameters.class);
  }

  public CompletableFuture<Parameters> subsumes(FhirQueryParams params) {
    return client.GET("/$subsumes?" + BaseHttpClient.toQueryParams(params), Parameters.class);
  }

  public CompletableFuture<Parameters> subsumes(Parameters params) {
    return client.POST("/$subsumes", params, Parameters.class);
  }

  public CompletableFuture<Parameters> findMatches(Parameters params) {
    return client.POST("/$find-matches", params, Parameters.class);
  }

  public CompletableFuture<Parameters> sync(Parameters params) {
    return client.POST("/$sync", params, Parameters.class);
  }
}
