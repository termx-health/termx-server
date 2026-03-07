package com.kodality.termx.core.sys.email;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class EmailTestResult {
  private boolean sent;
  private String error;
  private String message;
}
