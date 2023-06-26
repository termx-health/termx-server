package com.kodality.termserver.sys.space.diff;

import io.micronaut.core.annotation.Introspected;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
@Introspected
public class SpaceDiffRequest {
  private String spaceCode;
  private String packageCode;
  private String version;
}
