package com.kodality.termserver.ts;

import com.kodality.commons.client.BaseHttpClient;
import com.kodality.commons.client.HttpClient;
import com.kodality.commons.model.QueryResult;
import com.kodality.commons.util.JsonUtil;
import com.kodality.termserver.codesystem.CodeSystem;
import com.kodality.termserver.codesystem.CodeSystemAssociation;
import com.kodality.termserver.codesystem.CodeSystemEntityVersion;
import com.kodality.termserver.codesystem.CodeSystemEntityVersionQueryParams;
import com.kodality.termserver.codesystem.CodeSystemQueryParams;
import com.kodality.termserver.codesystem.CodeSystemSupplement;
import com.kodality.termserver.codesystem.CodeSystemVersion;
import com.kodality.termserver.codesystem.CodeSystemVersionQueryParams;
import com.kodality.termserver.codesystem.Concept;
import com.kodality.termserver.codesystem.ConceptQueryParams;
import com.kodality.termserver.codesystem.Designation;
import com.kodality.termserver.codesystem.EntityProperty;
import com.kodality.termserver.codesystem.EntityPropertyQueryParams;
import com.kodality.termserver.codesystem.EntityPropertyValue;
import java.util.List;
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

  public CompletableFuture<EntityPropertyValue> getEntityPropertyValue(String codeSystem, Long id) {
    return client.GET("/" + codeSystem + "/entity-property-values/" + id, EntityPropertyValue.class);
  }

  public CompletableFuture<Designation> getDesignation(String codeSystem, Long id) {
    return client.GET("/" + codeSystem + "/designations/" + id, Designation.class);
  }

  public CompletableFuture<CodeSystemAssociation> getAssociation(String codeSystem, Long id) {
    return client.GET("/" + codeSystem + "/associations/" + id, CodeSystemAssociation.class);
  }

  public CompletableFuture<List<CodeSystemSupplement>> getSupplements(String codeSystem) {
    return client.GET("/" + codeSystem + "/supplements", JsonUtil.getParametricType(List.class, CodeSystemSupplement.class));
  }

  public CompletableFuture<CodeSystemSupplement> getSupplement(String codeSystem, Long id) {
    return client.GET("/" + codeSystem + "/supplements/" + id, CodeSystemSupplement.class);
  }
}
