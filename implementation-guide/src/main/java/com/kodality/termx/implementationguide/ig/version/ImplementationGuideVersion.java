package com.kodality.termx.implementationguide.ig.version;

import com.kodality.termx.implementationguide.ig.version.group.ImplementationGuideGroup;
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
  private String githubUrl;
  private String emptyGithubUrl;
  private String template;
  private String algorithm;
  private List<ImplementationGuideVersionDependsOn> dependsOn;
  private List<ImplementationGuideGroup> groups;

  @Getter
  @Setter
  private static class ImplementationGuideVersionDependsOn {
    private String packageId;
    private String version;
    private String reason;
  }
}
