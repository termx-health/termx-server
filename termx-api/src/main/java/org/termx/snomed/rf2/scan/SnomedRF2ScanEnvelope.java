package org.termx.snomed.rf2.scan;

import io.micronaut.core.annotation.Introspected;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
@Introspected
public class SnomedRF2ScanEnvelope {
  private SnomedRF2ScanResult json;
  private String markdown;
}
