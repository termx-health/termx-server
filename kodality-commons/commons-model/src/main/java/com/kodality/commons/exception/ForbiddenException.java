package com.kodality.commons.exception;

import com.kodality.commons.CommonApiError;
import java.util.Map;

public class ForbiddenException extends ApiClientException {

  public ForbiddenException(String message) {
    super(CommonApiError.XX403.toIssue(Map.of("message", message)));
    setHttpStatus(403);
  }

}
