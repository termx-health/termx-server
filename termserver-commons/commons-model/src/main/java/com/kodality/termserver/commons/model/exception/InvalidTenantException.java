package com.kodality.termserver.commons.model.exception;

import com.kodality.termserver.commons.model.CommonApiError;
import java.util.Map;

public class InvalidTenantException extends ApiClientException {
  public InvalidTenantException() {
    super(CommonApiError.XX101.toIssue());
  }

  public InvalidTenantException(String details) {
    super(CommonApiError.XX101.toIssue(Map.of("details", details)));
  }

}
