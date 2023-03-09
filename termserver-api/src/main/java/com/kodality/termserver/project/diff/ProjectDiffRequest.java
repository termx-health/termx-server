package com.kodality.termserver.project.diff;

import io.micronaut.core.annotation.Introspected;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
@Introspected
public class ProjectDiffRequest {
  private String projectCode;
  private String packageCode;
  private String version;
}
