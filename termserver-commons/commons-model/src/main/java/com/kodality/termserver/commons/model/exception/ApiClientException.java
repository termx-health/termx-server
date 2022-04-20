package com.kodality.termserver.commons.model.exception;

import com.kodality.termserver.commons.model.model.Issue;

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
