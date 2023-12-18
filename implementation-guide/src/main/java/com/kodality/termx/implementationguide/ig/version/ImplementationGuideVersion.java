package com.kodality.termx.implementationguide.ig.version;

import com.kodality.termx.implementationguide.ig.version.group.ImplementationGuideGroup;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class ImplementationGuideVersion {
  private Long id;
  private String implementationGuide;
  private String version;
  private String status;
  private String packageId;
  private String fhirVersion;
  private ImplementationGuideVersionGithub github;
  private String template;
  private String algorithm;
  private OffsetDateTime date;
  private List<ImplementationGuideVersionDependsOn> dependsOn;
  private List<ImplementationGuideGroup> groups;

  @Getter
  @Setter
  public static class ImplementationGuideVersionDependsOn {
    private String packageId;
    private String version;
    private String reason;
  }

  @Getter
  @Setter
  public static class ImplementationGuideVersionGithub {
    private String repo;
    private String branch;
    private String init;
  }
}
