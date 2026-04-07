package org.termx.ts.codesystem;

import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class CodeSystemArtifactImpact {
  private String artifactType;
  private String artifactId;
  private String artifactVersion;
  private boolean dynamic;
  private boolean affected;
  private String reason;
  private OffsetDateTime snapshotCreatedAt;
  private CodeSystemVersionReference resolvedCodeSystemVersion;
  private CodeSystemVersionReference currentCodeSystemVersion;
}
