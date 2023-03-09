package com.kodality.termserver.project.overview;

import io.micronaut.core.annotation.Introspected;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
@Introspected
public class ProjectOverviewRequest {
  private String projectCode;
  private String packageCode;
  private String version;
}
