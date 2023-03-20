package com.kodality.termserver;

import com.kodality.commons.client.BaseHttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;

public class BinaryHttpClient extends BaseHttpClient {
  public BinaryHttpClient() {
    super();
  }

  public BinaryHttpClient(String baseUrl) {
    super(baseUrl);
  }

  public HttpResponse<byte[]> GET(String path) {
    HttpRequest request = builder(path).GET().build();
    return execute(request, BodyHandlers.ofByteArray());
  }
}
