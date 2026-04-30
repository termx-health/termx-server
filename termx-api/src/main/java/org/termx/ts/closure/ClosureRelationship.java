package org.termx.ts.closure;

import io.micronaut.core.annotation.Introspected;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Introspected
@Accessors(chain = true)
public class ClosureRelationship {
  private Long id;
  private Long closureId;
  private String codeSystem;
  private String childCode;
  private String parentCode;
  private Integer version;
}
