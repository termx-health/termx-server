package com.kodality.termx.modeler;

import com.kodality.commons.exception.ApiClientException;
import com.kodality.commons.exception.ApiException;
import com.kodality.commons.model.Issue;
import java.util.Map;
import lombok.Getter;

@Getter
public enum ApiError {
  MO101("MO101", "FHIR object is not accessible by the URL. Please validate URL(s)."),
  MO102("MO102", "StructureDefinition is missing or invalid."),
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
