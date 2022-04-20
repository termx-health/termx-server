package com.kodality.termserver.commons.model.exception;

import com.kodality.termserver.commons.model.model.Issue;

public class ApiServerException extends ApiException {

  public ApiServerException(int httpStatus, String message) {
    super(httpStatus, Issue.error(message));
  }

  public ApiServerException(String message) {
    this(500, message);
  }

}
