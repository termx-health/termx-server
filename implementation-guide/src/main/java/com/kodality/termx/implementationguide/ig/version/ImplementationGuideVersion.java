package com.kodality.termx.implementationguide.ig.version;

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
  private String fhirVersion;
  private String githubUrl;
  private String emptyGithubUrl;
  private String template;
  private String algorithm;
}
