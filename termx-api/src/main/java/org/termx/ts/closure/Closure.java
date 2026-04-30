package org.termx.ts.closure;

import io.micronaut.core.annotation.Introspected;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Introspected
@Accessors(chain = true)
public class Closure {
  private Long id;
  private String name;
  private Integer currentVersion;
}
