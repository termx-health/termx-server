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
public class SnomedRF2ModifiedConcept {
  private String conceptId;
  private List<SnomedRF2Designation> addedDesignations;
  private List<SnomedRF2Designation> removedDesignations;
  private List<SnomedRF2Attribute> addedAttributes;
  private List<SnomedRF2Attribute> removedAttributes;
}
