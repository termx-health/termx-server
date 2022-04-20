package com.kodality.termserver.commons.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.kodality.termserver.commons.util.JsonUtil;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

public class HttpClient extends BaseHttpClient {

  public HttpClient() {
    super();
  }

  public HttpClient(String baseUrl) {
    super(baseUrl);
  }

  public CompletableFuture<HttpResponse<String>> GET(String path) {
    HttpRequest request = builder(path).GET().build();
    return executeAsync(request);
  }

  public <T> CompletableFuture<T> GET(String path, Class<T> clazz) {
    return GET(path).thenApply(resp -> JsonUtil.fromJson(resp.body(), clazz));
  }

  public <T> CompletableFuture<T> GET(String path, JavaType type) {
    return GET(path).thenApply(resp -> JsonUtil.fromJson(resp.body(), type));
  }

  public <T> CompletableFuture<T> GET(String path, TypeReference<T> reference) {
    return GET(path).thenApply(resp -> JsonUtil.fromJson(resp.body(), reference));
  }

  public CompletableFuture<HttpResponse<String>> POST(String path, Object entity) {
    String json = JsonUtil.toJson(entity);
    HttpRequest request = builder(path).POST(json == null ? BodyPublishers.noBody() : BodyPublishers.ofString(json))
        .header("Content-Type", "application/json")
        .build();
    return executeAsync(request);
  }

  public <T> CompletableFuture<T> POST(String path, Object entity, Class<T> clazz) {
    return POST(path, entity).thenApply(resp -> JsonUtil.fromJson(resp.body(), clazz));
  }

  public <T> CompletableFuture<T> POST(String path, Object entity, JavaType type) {
    return POST(path, entity).thenApply(resp -> JsonUtil.fromJson(resp.body(), type));
  }

  public <T> CompletableFuture<T> POST(String path, Object entity, TypeReference<T> reference) {
    return POST(path, entity).thenApply(resp -> JsonUtil.fromJson(resp.body(), reference));
  }

  public CompletableFuture<HttpResponse<String>> PUT(String path, Object entity) {
    String json = JsonUtil.toJson(entity);
    HttpRequest request = builder(path).PUT(BodyPublishers.ofString(json)).header("Content-Type", "application/json").build();
    return executeAsync(request);
  }

  public <T> CompletableFuture<T> PUT(String path, Object entity, Class<T> clazz) {
    return PUT(path, entity).thenApply(resp -> JsonUtil.fromJson(resp.body(), clazz));
  }

  public <T> CompletableFuture<T> PUT(String path, Object entity, JavaType type) {
    return PUT(path, entity).thenApply(resp -> JsonUtil.fromJson(resp.body(), type));
  }

  public <T> CompletableFuture<T> PUT(String path, Object entity, TypeReference<T> reference) {
    return PUT(path, entity).thenApply(resp -> JsonUtil.fromJson(resp.body(), reference));
  }

  public CompletableFuture<HttpResponse<String>> DELETE(String path) {
    HttpRequest request = builder(path).DELETE().build();
    return executeAsync(request);
  }

  public CompletableFuture<HttpResponse<String>> DELETE(String path, Object entity) {
    String json = JsonUtil.toJson(entity);
    HttpRequest request = builder(path).method("DELETE", BodyPublishers.ofString(json))
        .header("Content-Type", "application/json")
        .build();
    return executeAsync(request);
  }

}
