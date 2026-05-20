package org.termx.snomed.rf2;

import io.micronaut.core.annotation.Introspected;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
@Introspected
public class SnomedImportTracking {
  private Long id;
  private String snowstormJobId;
  private String branchPath;
  private String type;
  private String status;
  private OffsetDateTime started;
  private OffsetDateTime finished;
  private String errorMessage;
  private boolean notified;
  /**
   * Per-import lifecycle log lines surfaced under "Success Messages" in the post-import
   * email (same pattern as {@code ImportLog.successes} for LOINC). Populated by the
   * upload service as the import moves through createImportJob → uploadRF2File →
   * tracking-recorded phases, then by the polling service when Snowstorm reports a
   * terminal status. Stored as a Postgres {@code text[]} on
   * {@code sys.snomed_import_tracking.details}.
   */
  private List<String> details = new ArrayList<>();
}
