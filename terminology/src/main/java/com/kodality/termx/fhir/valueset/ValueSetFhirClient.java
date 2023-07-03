package com.kodality.termx.fhir.valueset;

import com.kodality.zmei.fhir.FhirMapper;
import com.kodality.zmei.fhir.client.FhirResourceClient;
import com.kodality.zmei.fhir.resource.other.Parameters;
import com.kodality.zmei.fhir.resource.terminology.ValueSet;
import com.kodality.zmei.fhir.search.FhirQueryParams;
import java.net.http.HttpRequest;
import java.util.concurrent.CompletableFuture;

public class ValueSetFhirClient extends FhirResourceClient<ValueSet> {

  public ValueSetFhirClient(String url) {
    super(url + "/fhir/ValueSet", ValueSet.class);
  }

  public CompletableFuture<ValueSet> expand(String code, FhirQueryParams params) {
    HttpRequest request = builder("/" + code + "/$expand?" + toQueryParams(params)).GET().build();
    return executeAsync(request).thenApply(r -> FhirMapper.fromJson(r.body(), ValueSet.class));
  }

  public CompletableFuture<ValueSet> expand(FhirQueryParams params) {
    HttpRequest request = builder("/$expand?" + toQueryParams(params)).GET().build();
    return executeAsync(request).thenApply(r -> FhirMapper.fromJson(r.body(), ValueSet.class));
  }

  public CompletableFuture<Parameters> validateCode(FhirQueryParams params) {
    HttpRequest request = builder("/$validate-code?" + toQueryParams(params)).GET().build();
    return executeAsync(request).thenApply(r -> FhirMapper.fromJson(r.body(), Parameters.class));
  }

  public CompletableFuture<Parameters> sync(Parameters params) {
    HttpRequest request = builder("/$sync?" + toQueryParams(params)).GET().build();
    return executeAsync(request).thenApply(r -> FhirMapper.fromJson(r.body(), Parameters.class));
  }
}
