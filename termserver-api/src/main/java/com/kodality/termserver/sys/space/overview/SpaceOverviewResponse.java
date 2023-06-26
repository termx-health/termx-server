package com.kodality.termserver.sys.space.overview;

import io.micronaut.core.annotation.Introspected;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
@Introspected
public class SpaceOverviewResponse {
  private String content;
}
