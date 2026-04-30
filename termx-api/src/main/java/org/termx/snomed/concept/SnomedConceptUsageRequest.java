package org.termx.snomed.concept;

import io.micronaut.core.annotation.Introspected;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Introspected
public class SnomedConceptUsageRequest {
  private List<String> codes;
}
