package com.kodality.zmei.fhir.client;

import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import lombok.Data;
import lombok.Getter;
import lombok.experimental.Accessors;

@Getter
public class FhirClientError extends RuntimeException {
  private final HttpRequest request;
  private final Response response;

  public FhirClientError(HttpRequest request, Response response) {
    super("Request to " + request.uri() + " returned " + response.statusCode + "\n " + response.body);
    this.request = request;
    this.response = response;
  }

  @Data
  @Accessors(fluent = true)
  public static class Response {
    private final int statusCode;
    private final HttpHeaders headers;
    private final String body;
  }
}
