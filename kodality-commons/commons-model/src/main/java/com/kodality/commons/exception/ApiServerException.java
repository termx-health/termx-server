package com.kodality.commons.exception;

import com.kodality.commons.model.Issue;

public class ApiServerException extends ApiException {

  public ApiServerException(int httpStatus, String message) {
    super(httpStatus, Issue.error(message));
  }

  public ApiServerException(String message) {
    this(500, message);
  }

}
