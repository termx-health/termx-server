package org.termx.snomed.rf2.scan;

import io.micronaut.core.annotation.Introspected;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
@Introspected
public class SnomedRF2ScanResult {
  private String branchPath;
  private String rf2Type;
  private String releaseEffectiveTime;
  private OffsetDateTime scannedAt;
  private Long uploadCacheId;
  private Stats stats;
  private List<SnomedRF2NewConcept> newConcepts;
  private List<SnomedRF2ModifiedConcept> modifiedConcepts;
  private List<SnomedRF2InvalidatedConcept> invalidatedConcepts;

  @Getter
  @Setter
  @Accessors(chain = true)
  @Introspected
  public static class Stats {
    private int conceptsAdded;
    private int conceptsModified;
    private int conceptsInvalidated;
    private int descriptionsAdded;
    private int descriptionsInvalidated;
    private int relationshipsAdded;
    private int relationshipsInvalidated;
  }
}
