package org.termx.snomed.concept;

import io.micronaut.core.annotation.Introspected;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
@Introspected
public class SnomedConceptUsage {
  private String resourceType;
  private String resourceId;
  private String resourceVersion;
  private String conceptCode;
  private String location;
}
