package com.kodality.termserver.ts.mapset;

import com.kodality.termserver.ts.codesystem.CodeSystemEntityVersion;
import io.micronaut.core.annotation.Introspected;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Introspected
@Accessors(chain = true)
public class MapSetAssociation extends MapSetEntity {
  private CodeSystemEntityVersion source;
  private CodeSystemEntityVersion target;
  private String associationType;
  private String status;
}
