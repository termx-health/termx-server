package com.kodality.termserver.commons.client;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.concurrent.CompletableFuture;

public class BinaryHttpClient extends BaseHttpClient {
  public BinaryHttpClient() {
    super();
  }

  public BinaryHttpClient(String baseUrl) {
    super(baseUrl);
  }

  public CompletableFuture<HttpResponse<byte[]>> GET(String path) {
    HttpRequest request = builder(path).GET().build();
    return executeAsync(request, BodyHandlers.ofByteArray());
  }
}
