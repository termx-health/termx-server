package org.termx.ts.codesystem;

import io.micronaut.core.annotation.Introspected;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Introspected
@Accessors(chain = true)
public class ConceptTreeItem extends Concept {
  private String parentCode;
  private boolean matched;
}
