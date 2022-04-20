package com.kodality.termserver.commons.client;

import com.kodality.termserver.commons.util.JsonUtil;
import com.kodality.termserver.commons.model.model.Issue;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.util.List;
import lombok.Data;
import lombok.Getter;
import lombok.experimental.Accessors;

@Getter
public class HttpClientError extends RuntimeException {
  private final HttpRequest request;
  private final Response response;
  private final List<Issue> issues;

  HttpClientError(HttpRequest request, Response response) {
    super("Request to " + request.uri() + " returned " + response.statusCode + "\n " + response.body);
    this.request = request;
    this.response = response;
    this.issues = toIssues(response);
  }

  private List<Issue> toIssues(Response response) {
    boolean isJson = response.headers() != null && response.headers().
        firstValue("Content-Type")
        .filter("application/json"::equals)
        .isPresent();

    if (isJson) {
      try {
        return JsonUtil.fromJson(response.body(), JsonUtil.getListType(Issue.class));
      } catch (Throwable e) {
        return List.of();
      }
    } else {
      return List.of();
    }
  }

  @Data
  @Accessors(fluent = true)
  public static class Response {
    private final int statusCode;
    private final HttpHeaders headers;
    private final String body;
  }
}
