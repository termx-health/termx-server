package org.termx.ts.valueset;

import org.termx.ts.codesystem.CodeSystemVersionReference;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class ValueSetSnapshotDependency {
  private Long ruleId;
  private String codeSystem;
  private boolean dynamic;
  private CodeSystemVersionReference codeSystemVersion;
}
