package com.kodality.termserver.codesystem;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class CodeSystemAssociation extends CodeSystemEntity {
  private String associationType;
  private String status;
  private Long targetId;
  private Long sourceId;

  private String targetCode;
}
