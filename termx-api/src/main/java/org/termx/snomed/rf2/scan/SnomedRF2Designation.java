package org.termx.snomed.rf2.scan;

import io.micronaut.core.annotation.Introspected;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
@Introspected
public class SnomedRF2Designation {
  private String descriptionId;
  private String term;
  private String type;
  private String language;
  private String acceptability;
  private boolean active;
  private String effectiveTime;
}
