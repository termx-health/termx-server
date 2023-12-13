package com.kodality.termx.snomed;

import com.kodality.commons.exception.ApiClientException;
import com.kodality.commons.exception.ApiException;
import com.kodality.commons.model.Issue;
import java.util.Map;
import lombok.Getter;

@Getter
public enum ApiError {
  SN101("SN101", "Branch name is required"),
  SN102("SN102", "Branch name can contain only: A-Z charaters, numbers, slash and hyphen"),
  ;

  private String code;
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
