package com.kodality.termx.implementationguide;

import com.kodality.commons.exception.ApiClientException;
import com.kodality.commons.exception.ApiException;
import com.kodality.commons.model.Issue;
import java.util.Map;
import lombok.Getter;

public enum ApiError {
  IG101("IG101", "Could not create not draft version."),
  IG102("IG102", "Version '{{version}}' already exists.");

  @Getter
  private String code;
  @Getter
  private String message;

  ApiError(String code, String message) {
    this.code = code;
    this.message = message;
  }

  public ApiException toApiException() {
    return new ApiClientException(code, message);
  }

  public ApiException toApiException(Map<String, Object> params) {
    return new ApiClientException(toIssue(params));
  }

  public Issue toIssue() {
    return toIssue(Map.of());
  }

  public Issue toIssue(Map<String, Object> params) {
    return Issue.error(code, message).setParams(params);
  }
}
