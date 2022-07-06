package com.kodality.termserver;

import com.kodality.commons.exception.ApiClientException;
import com.kodality.commons.exception.ApiException;
import com.kodality.commons.model.Issue;
import java.util.Map;
import lombok.Getter;

public enum ApiError {

  TE101("TE101", "Could not modify not draft version."),
  TE102("TE102", "Draft version '{{version}}' already exists."),
  TE103("TE103", "Can't activate version, active version '{{version}}' has overlapping periods."),
  TE104("TE104", "Version '{{version}}' is already created and active."),
  TE105("TE105", "Version with id '{{version}}' doesn't exist."),
  TE106("TE106", "Url not provided."),
  TE107("TE107", "Wrong resource type."),

  TE201("TE201", "Code system '{{codeSystem}}' not found."),
  TE202("TE202", "Version '{{version}}' of code system '{{codeSystem}}' doesn't exist."),

  TE301("TE301", "Version '{{version}}' of value set '{{valueSet}}' doesn't exist."),
  TE302("TE302", "Value set '{{valueSet}}' doesn't have active version."),

  TE401("TE401", "Version '{{version}}' of map set '{{mapSet}}' doesn't exist."),

  TE501("TE501", "Naming system '{{namingSystem}}' not found."),

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
    ApiClientException exception = new ApiClientException(code, message);
    exception.getIssues().forEach(issue -> issue.setParams(Map.of())); //TODO fix in commons
    return exception;
  }

  public ApiException toApiException(Map<String, Object> params) {
    return new ApiClientException(toIssue(params));
  }

  public Issue toIssue(Map<String, Object> params) {
    return Issue.error(code, message).setParams(params);
  }

}
