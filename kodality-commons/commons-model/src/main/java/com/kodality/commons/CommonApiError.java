package com.kodality.commons;

import com.kodality.commons.exception.ApiClientException;
import com.kodality.commons.exception.ApiException;
import com.kodality.commons.model.Issue;
import java.util.Map;
import lombok.Getter;

public enum CommonApiError {

  XX100("XX100", "System error"),
  XX102("XX102", "Object validation error"),

  XX403("XX403", "Forbidden: {{message}}"),
  XX404("XX404", "Not Found: {{message}}");

  @Getter
  private String code;
  @Getter
  private String message;

  private CommonApiError(String code, String message) {
    this.code = code;
    this.message = message;
  }

  public ApiException toApiException() {
    return new ApiClientException(toIssue());
  }

  public ApiException toApiException(Map<String, Object> params) {
    return new ApiClientException(toIssue(params));
  }

  public Issue toIssue() {
    return Issue.error(code, message);
  }

  public Issue toIssue(Map<String, Object> params) {
    return Issue.error(code, message).setParams(params);
  }

}
