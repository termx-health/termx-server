package org.termx.snomed.rf2;

import io.micronaut.core.annotation.Introspected;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
@Introspected
public class SnomedRF2Upload {
  private Long id;
  private String branchPath;
  private String rf2Type;
  private boolean createCodeSystemVersion;
  private String filename;
  private Long zipSize;
  private byte[] zipData;
  /**
   * Set when the scan came in via a Bob-stored archive (POST /imports/scan/from-archive).
   * In that case {@code zipData} is null and the "proceed with import" path re-streams from
   * Bob → Snowstorm rather than reading {@code zipData}. Mutually exclusive with {@code zipData}.
   */
  private String bobObjectUuid;
  private Long scanLorqueId;
  private boolean imported;
  private OffsetDateTime started;
  private OffsetDateTime importedAt;
}
