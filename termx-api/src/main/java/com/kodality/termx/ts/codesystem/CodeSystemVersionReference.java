package com.kodality.termx.ts.codesystem;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class CodeSystemVersionReference {
  private Long id;
  private String version;
  private String status;
  private String preferredLanguage;
}
