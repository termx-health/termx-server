package com.kodality.commons.exception;

import com.kodality.commons.model.Issue;

public class ApiClientException extends ApiException {

  public ApiClientException(String code, String message) {
    super(400, Issue.error(code, message));
  }

  public ApiClientException(String message) {
    super(400, Issue.error(message));
  }

  public ApiClientException(Issue issue) {
    super(400, issue);
  }

}
