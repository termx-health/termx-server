package com.kodality.termx.snomed.codesystem;

import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SnomedCodeSystem {
  private String shortName;
  private String branchPath;
  private Map<String, String> languages;
  private SnomedCodeSystemVersion latestVersion;
  private List<SnomedCodeSystemVersion> versions;

  @Getter
  @Setter
  public static class SnomedCodeSystemVersion {
    private String shortName;
    private String parentBranchPath;
    private String version;
    private String description;
    private String branchPath;
  }
}
