package com.kodality.termserver.commons.model.exception;

import com.kodality.termserver.commons.model.model.Issue;
import java.util.Collection;
import java.util.Collections;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import static java.util.stream.Collectors.joining;

@Getter
@Setter
@Accessors(chain = true)
public class ApiException extends RuntimeException {
  private int httpStatus;
  private Collection<Issue> issues;

  public ApiException() {
    //
  }

  public ApiException(int httpStatus, Collection<Issue> issues) {
    super(getMessage(issues));
    this.httpStatus = httpStatus;
    this.issues = issues;
  }

  public ApiException(int httpStatus, Issue issue) {
    this(httpStatus, Collections.singletonList(issue));
  }

  private static String getMessage(Collection<Issue> issues) {
    return issues == null
        ? "Unknown api exception"
        : issues.stream().map(i -> i.getCode() + ": " + i.getMessage()).collect(joining(", "));
  }

}
