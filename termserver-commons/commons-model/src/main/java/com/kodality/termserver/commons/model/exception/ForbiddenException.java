package com.kodality.termserver.commons.model.exception;

import com.kodality.termserver.commons.model.CommonApiError;
import java.util.Map;

public class ForbiddenException extends ApiClientException {

  public ForbiddenException(String message) {
    super(CommonApiError.XX403.toIssue(Map.of("message", message)));
    setHttpStatus(403);
  }

}
