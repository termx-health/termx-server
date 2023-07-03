package com.kodality.termx.ts;

import com.kodality.commons.client.BaseHttpClient;
import com.kodality.commons.client.HttpClient;
import com.kodality.commons.model.QueryResult;
import com.kodality.commons.util.JsonUtil;
import com.kodality.termx.ts.codesystem.CodeSystem;
import com.kodality.termx.ts.codesystem.CodeSystemEntityVersion;
import com.kodality.termx.ts.codesystem.CodeSystemEntityVersionQueryParams;
import com.kodality.termx.ts.codesystem.CodeSystemQueryParams;
import com.kodality.termx.ts.codesystem.CodeSystemVersion;
import com.kodality.termx.ts.codesystem.CodeSystemVersionQueryParams;
import com.kodality.termx.ts.codesystem.Concept;
import com.kodality.termx.ts.codesystem.ConceptQueryParams;
import com.kodality.termx.ts.codesystem.EntityProperty;
import com.kodality.termx.ts.codesystem.EntityPropertyQueryParams;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class CodeSystemClient {
  protected HttpClient client;

  public CodeSystemClient(){}

  public CodeSystemClient(String url, Function<String, HttpClient> clientProvider) {
    client = clientProvider.apply(url + "/ts/code-systems");
  }

  public CompletableFuture<QueryResult<CodeSystem>> queryCodeSystems(CodeSystemQueryParams params) {
    return client.GET("?" + BaseHttpClient.toQueryParams(params), JsonUtil.getParametricType(QueryResult.class, CodeSystem.class));
  }

  public CompletableFuture<CodeSystem> getCodeSystem(String codeSystem) {
    return client.GET("/" + codeSystem, CodeSystem.class);
  }

  public CompletableFuture<CodeSystem> getCodeSystem(String codeSystem, boolean decorated) {
    return client.GET("/" + codeSystem + (decorated ? "?decorate=true" : ""), CodeSystem.class);
  }

  public CompletableFuture<QueryResult<CodeSystemVersion>> queryCodeSystemVersions(String codeSystem, CodeSystemVersionQueryParams params) {
    return client.GET("/" + codeSystem + "/versions?" + BaseHttpClient.toQueryParams(params), JsonUtil.getParametricType(QueryResult.class, CodeSystemVersion.class));
  }

  public CompletableFuture<CodeSystemVersion> getCodeSystemVersion(String codeSystem, String version) {
    return client.GET("/" + codeSystem + "/versions/" + version, CodeSystemVersion.class);
  }

  public CompletableFuture<QueryResult<Concept>> queryConcepts(String codeSystem, ConceptQueryParams params) {
    return client.GET("/" + codeSystem + "/concepts?" + BaseHttpClient.toQueryParams(params), JsonUtil.getParametricType(QueryResult.class, Concept.class));
  }

  public CompletableFuture<Concept> getConcept(String codeSystem, String code) {
    return client.GET("/" + codeSystem + "/concepts/" + code, Concept.class);
  }

  public CompletableFuture<Concept> getConcept(String codeSystem, String version, String code) {
    return client.GET("/" + codeSystem + "/versions/" + version + "/concepts/" + code, Concept.class);
  }

  public CompletableFuture<QueryResult<CodeSystemEntityVersion>> queryEntityVersions(String codeSystem, CodeSystemEntityVersionQueryParams params) {
    return client.GET("/" + codeSystem + "/entity-versions?" + BaseHttpClient.toQueryParams(params), JsonUtil.getParametricType(QueryResult.class, CodeSystemEntityVersion.class));
  }

  public CompletableFuture<QueryResult<EntityProperty>> queryEntityProperties(String codeSystem, EntityPropertyQueryParams params) {
    return client.GET("/" + codeSystem + "/entity-properties?" + BaseHttpClient.toQueryParams(params), JsonUtil.getParametricType(QueryResult.class, EntityProperty.class));
  }

  public CompletableFuture<EntityProperty> getEntityProperty(String codeSystem, Long id) {
    return client.GET("/" + codeSystem + "/entity-properties/" + id, EntityProperty.class);
  }
}
