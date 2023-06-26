package com.kodality.termserver.sys.space.overview;

import io.micronaut.core.annotation.Introspected;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
@Introspected
public class SpaceOverviewRequest {
  private String spaceCode;
  private String packageCode;
  private String version;
}
