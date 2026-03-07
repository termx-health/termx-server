package com.kodality.termx.snomed.rf2;

import io.micronaut.core.annotation.Introspected;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
@Introspected
public class SnomedImportTracking {
  private Long id;
  private String snowstormJobId;
  private String branchPath;
  private String type;
  private String status;
  private OffsetDateTime started;
  private OffsetDateTime finished;
  private String errorMessage;
  private boolean notified;
}
