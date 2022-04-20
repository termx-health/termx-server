package com.kodality.termserver.commons.model.exception;

import com.kodality.termserver.commons.model.CommonApiError;
import java.util.Map;

public class NotFoundException extends ApiClientException {

  public NotFoundException(String message) {
    super(CommonApiError.XX404.toIssue(Map.of("message", message)));
    setHttpStatus(400);
  }

}
