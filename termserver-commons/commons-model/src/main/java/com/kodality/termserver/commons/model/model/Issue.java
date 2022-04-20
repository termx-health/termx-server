package com.kodality.termserver.commons.model.model;

import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class Issue {
  private Severity severity;
  private String code;
  private String message;
  private Map<String, Object> params;

  public Issue() {
    //
  }

  public Issue(Severity severity) {
    this.severity = severity;
  }

  public Issue(Severity severity, String message) {
    this(severity);
    this.message = message;
  }

  public Issue(Severity severity, String code, String message) {
    this(severity, message);
    this.code = code;
  }

  public static Issue error(String message) {
    return new Issue(Severity.ERROR, message);
  }

  public static Issue error(String code, String message) {
    return new Issue(Severity.ERROR, code, message);
  }

}
