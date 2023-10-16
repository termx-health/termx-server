
package com.kodality.termx.terminology.terminology.codesystem.validator;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CodeSystemUniquenessValidatorRequest {
  private String codeSystem;
  private Long versionId;
  private boolean designations;
  private boolean properties;
  private boolean ignoreEmptyProperties;
}
