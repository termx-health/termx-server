package org.termx.taskforge;

import com.kodality.commons.exception.ApiClientException;
import com.kodality.commons.exception.ApiException;
import com.kodality.commons.model.Issue;
import com.kodality.commons.util.MapUtil;
import lombok.Getter;

public enum ApiError {

  TF100("TF100", "Cannot change tasks project"),
  TF101("TF101", "Invalid transition {{from}} -> {{to}}"),
  TF102("TF102", "Cannot change task executions task reference"),

  RC000("", "");

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

  public ApiException toApiException(String... params) {
    return new ApiClientException(Issue.error(code, message).setParams(MapUtil.toMap((Object[]) params)));
  }
}
