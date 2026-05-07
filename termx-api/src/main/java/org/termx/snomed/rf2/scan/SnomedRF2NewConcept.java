package org.termx.snomed.rf2.scan;

import io.micronaut.core.annotation.Introspected;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
@Introspected
public class SnomedRF2NewConcept {
  private String conceptId;
  private String effectiveTime;
  private String moduleId;
  private String definitionStatusId;
  private List<SnomedRF2Designation> designations;
  private List<SnomedRF2Attribute> attributes;
}
