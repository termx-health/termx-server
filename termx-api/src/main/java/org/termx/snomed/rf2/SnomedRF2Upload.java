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
  private Long scanLorqueId;
  private boolean imported;
  private OffsetDateTime started;
  private OffsetDateTime importedAt;
}
