package org.termx.snomed.rf2.scan;

import io.micronaut.core.annotation.Introspected;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
@Introspected
public class SnomedRF2Attribute {
  private String relationshipId;
  private String typeId;
  private String destinationId;
  private Integer relationshipGroup;
  private String characteristicTypeId;
  private String modifierId;
  private boolean active;
  private String effectiveTime;
}
