package com.kodality.termx.core;

import com.kodality.commons.exception.ApiClientException;
import com.kodality.commons.exception.ApiException;
import com.kodality.commons.model.Issue;
import java.util.Map;
import lombok.Getter;

public enum ApiError {
  TC101("TC101", "Current installation is already defined."),
  TC102("TC102", "Requested resource type is not implemented."),
  TC103("TC103", "Project not specified."),
  TC104("TC104", "Package or package version not specified."),
  TC105("TC105", "Terminology server for current installation is not defined."),
  TC106("TC106", "Import failed"),
  TC107("TC107", "Space Github directories must be unique"),
  TC108("TC108", "Space code '{{code}}' is already used."),
  TC109("TC109", "Server cannot contain multiple '{{name}}' headers"),
  TC110("TC110", "Release code '{{code}}' is already used."),
  TC111("TC111", "Resource sync failed."),
  TC112("TC112", "Checklist rule code '{{code}}' is already used."),
  TC113("TC113", "Either resourceType and resourceId or checklistId should be provided to validate rules"),
  ;

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
