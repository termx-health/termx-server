package com.kodality.termx.snomed.rf2;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SnomedImportRequest {
  private String branchPath;
  private boolean createCodeSystemVersion;
  private String type;
}
