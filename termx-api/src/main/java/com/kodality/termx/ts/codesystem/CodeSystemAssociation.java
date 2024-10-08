package com.kodality.termx.ts.codesystem;

import io.micronaut.core.annotation.Introspected;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Introspected
@Accessors(chain = true)
public class CodeSystemAssociation extends CodeSystemEntity {
  private String associationType;
  private String status;
  private Long targetId;
  private Long sourceId;
  private Integer orderNumber;

  private String sourceCode;
  private String targetCode;

  private boolean supplement;
}
