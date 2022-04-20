package com.kodality.termserver;

import com.kodality.termserver.commons.model.exception.ApiClientException;
import com.kodality.termserver.commons.model.exception.ApiException;
import com.kodality.termserver.commons.model.model.Issue;
import java.util.Map;
import lombok.Getter;

public enum ApiError {

  TE101("TE101", "Could not modify not draft version."),
  TE102("TE102", "Draft version '{{version}}' already exists in '{{codeSystem}}' codesystem."),
  TE103("TE103", "Can't activate version, active version '{{version}}' has overlapping periods."),
  TE104("TE104", "Version '{{version}}' of '{{codeSystem}}' doesn't exist."),
  TE105("TE105", "Version '{{version}}' is already created and active.")
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

  public Issue toIssue(Map<String, Object> params) {
    return Issue.error(code, message).setParams(params);
  }

}
