package com.kodality.termserver.ts;

import com.kodality.commons.client.BaseHttpClient;
import com.kodality.commons.client.HttpClient;
import com.kodality.commons.model.QueryResult;
import com.kodality.commons.util.JsonUtil;
import com.kodality.termserver.codesystem.Concept;
import com.kodality.termserver.codesystem.ConceptQueryParams;
import com.kodality.termserver.valueset.ValueSet;
import com.kodality.termserver.valueset.ValueSetExpandRequest;
import com.kodality.termserver.valueset.ValueSetQueryParams;
import com.kodality.termserver.valueset.ValueSetVersion;
import com.kodality.termserver.valueset.ValueSetVersionConcept;
import com.kodality.termserver.valueset.ValueSetVersionQueryParams;
import com.kodality.termserver.valueset.ValueSetVersionRuleSet.ValueSetVersionRule;
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

  public CompletableFuture<QueryResult<Concept>> queryConcepts(String valueSet, ConceptQueryParams params) {
    return client.GET("/" + valueSet + "/concepts?" + BaseHttpClient.toQueryParams(params), JsonUtil.getParametricType(QueryResult.class, Concept.class));
  }

  public CompletableFuture<ValueSetVersionConcept> getValueSetConcept(String valueSet, String version, Long id) {
    return client.GET("/" + valueSet + "/versions/" + version + "/concepts/" + id, ValueSetVersionConcept.class);
  }

  public CompletableFuture<List<ValueSetVersionConcept>> expand(ValueSetExpandRequest request) {
    return client.POST("/expand", request, JsonUtil.getParametricType(List.class, ValueSetVersionConcept.class));
  }

  public CompletableFuture<ValueSetVersionRule> getRule(String valueSet, Long id) {
    return client.GET("/" + valueSet + "/rules/" + id, ValueSetVersionRule.class);
  }

}
