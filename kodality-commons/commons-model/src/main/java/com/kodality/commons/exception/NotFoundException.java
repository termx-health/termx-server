package com.kodality.commons.exception;

import com.kodality.commons.CommonApiError;
import java.util.Map;

public class NotFoundException extends ApiClientException {
  
  public NotFoundException(String message) {
    super(CommonApiError.XX404.toIssue(Map.of("message", message)));
    setHttpStatus(400);
  }

  /**
   * Generates messages as: "{objectName} not found by {key}"
   */
  public NotFoundException(String objectName, Object key) {
    this(objectName + " not found by " + key);
  }

}
