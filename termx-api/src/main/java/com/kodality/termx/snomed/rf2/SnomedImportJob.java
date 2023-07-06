package com.kodality.termx.snomed.rf2;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SnomedImportJob {
  private String branchPath;
  private boolean createCodeSystemVersion;
  private String errorMessage;
  private boolean internalRelease;
  private List<String> moduleIds;
  private String status;
  private String type;
}
