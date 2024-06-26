package com.kodality.termx.snomed.codesystem;

import com.kodality.termx.snomed.concept.SnomedConcept.SnomedConceptName;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SnomedCodeSystem {
  private String name;
  private String shortName;
  private String branchPath;
  private String countryCode;
  private String dependantVersionEffectiveTime;
  private boolean dailyBuildAvailable;
  private Map<String, String> languages;
  private SnomedCodeSystemVersion latestVersion;
  private List<SnomedCodeSystemVersion> versions;
  private List<SnomedCodeSystemModule> modules;

  @Getter
  @Setter
  public static class SnomedCodeSystemVersion {
    private String shortName;
    private OffsetDateTime importDate;
    private String dependantVersionEffectiveTime;
    private String parentBranchPath;
    private String version;
    private Long effectiveDate;
    private String description;
    private String branchPath;
  }

  @Getter
  @Setter
  public static class SnomedCodeSystemModule {
    private String conceptId;
    private String moduleId;
    private SnomedConceptName fsn;
    private SnomedConceptName pt;
  }
}
