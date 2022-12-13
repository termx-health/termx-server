package com.kodality.termserver.fhir.valueset;

import com.kodality.commons.client.BaseHttpClient;
import com.kodality.commons.client.HttpClient;
import com.kodality.zmei.fhir.resource.infrastructure.Parameters;
import com.kodality.zmei.fhir.resource.other.Bundle;
import com.kodality.zmei.fhir.resource.terminology.ValueSet;
import com.kodality.zmei.fhir.search.FhirQueryParams;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class ValueSetFhirClient {
  protected HttpClient client;

  public ValueSetFhirClient(){}

  public ValueSetFhirClient(String url, Function<String, HttpClient> clientProvider) {
    client = clientProvider.apply(url + "/fhir/CodeSystem");
  }

  public CompletableFuture<Bundle> search(FhirQueryParams params) {
    return client.GET("?" + BaseHttpClient.toQueryParams(params), Bundle.class);
  }

  public CompletableFuture<ValueSet> save(FhirQueryParams params, ValueSet valueSet) {
    return client.PUT("?" + BaseHttpClient.toQueryParams(params), valueSet, ValueSet.class);
  }

  public CompletableFuture<ValueSet> expand(FhirQueryParams params) {
    return client.GET("/$expand?" + BaseHttpClient.toQueryParams(params), ValueSet.class);
  }

  public CompletableFuture<Parameters> validateCode(FhirQueryParams params) {
    return client.GET("/$validate-code?" + BaseHttpClient.toQueryParams(params), Parameters.class);
  }

  public CompletableFuture<Parameters> sync(Parameters params) {
    return client.POST("/$sync", params, Parameters.class);
  }
}
