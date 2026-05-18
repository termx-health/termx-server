package org.termx.ts.codesystem;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Nullable;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Introspected
@Accessors(chain = true)
public class CodingValueUpdateCandidate {
  private Long propertyValueId;
  private Long codeSystemEntityVersionId;
  private String entityProperty;
  private String targetCodeSystem;
  private String targetCode;
  @Nullable
  private String currentVersion;
  @Nullable
  private String candidateVersion;
  @Nullable
  private String candidateStatus;
}
