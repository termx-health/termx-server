package com.kodality.termx.ts;

import com.kodality.commons.client.BaseHttpClient;
import com.kodality.commons.client.HttpClient;
import com.kodality.commons.model.QueryResult;
import com.kodality.commons.util.JsonUtil;
import com.kodality.termx.ts.valueset.ValueSet;
import com.kodality.termx.ts.valueset.ValueSetExpandRequest;
import com.kodality.termx.ts.valueset.ValueSetQueryParams;
import com.kodality.termx.ts.valueset.ValueSetVersion;
import com.kodality.termx.ts.valueset.ValueSetVersionConcept;
import com.kodality.termx.ts.valueset.ValueSetVersionQueryParams;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class ValueSetClient {
  protected HttpClient client;

  public ValueSetClient(){}

  public ValueSetClient(String url, Function<String, HttpClient> clientProvider) {
    client = clientProvider.apply(url + "/ts/value-sets");
  }

  public CompletableFuture<QueryResult<ValueSet>> queryValueSets(ValueSetQueryParams params) {
    return client.GET("?" + BaseHttpClient.toQueryParams(params), JsonUtil.getParametricType(QueryResult.class, ValueSet.class));
  }

  public CompletableFuture<ValueSet> getValueSet(String valueSet) {
    return client.GET("/" + valueSet, ValueSet.class);
  }

  public CompletableFuture<QueryResult<ValueSetVersion>> queryValueSetVersions(String valueSet, ValueSetVersionQueryParams params) {
    return client.GET("/" + valueSet + "/versions?" + BaseHttpClient.toQueryParams(params), JsonUtil.getParametricType(QueryResult.class, ValueSetVersion.class));
  }

  public CompletableFuture<ValueSetVersion> getValueSetVersion(String valueSet, String version) {
    return client.GET("/" + valueSet + "/versions/" + version, ValueSetVersion.class);
  }

  public CompletableFuture<List<ValueSetVersionConcept>> expand(ValueSetExpandRequest request) {
    return client.POST("/expand", request, JsonUtil.getParametricType(List.class, ValueSetVersionConcept.class));
  }

}
