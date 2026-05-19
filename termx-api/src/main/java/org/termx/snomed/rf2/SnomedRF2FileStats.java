package org.termx.snomed.rf2;

import io.micronaut.core.annotation.Introspected;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Per-entry data-row counts for a SNOMED RF2 zip — the "Files" panel of the archive detail
 * page. Walks the zip's text entries, drops the header line, and reports each entry whose
 * remaining row count is &gt; 0. Cheap (single streaming pass, no per-row buffering), so it's
 * served by a synchronous endpoint.
 */
@Getter
@Setter
@Accessors(chain = true)
@Introspected
public class SnomedRF2FileStats {
  /** Total bytes of the archive (from BobObject storage), echoed for the UI header. */
  private Long archiveSize;
  /** Total zip entries the parser saw — includes ones filtered out (header-only / empty). */
  private Integer entriesScanned;
  /** Entries whose data-row count is &gt; 0, sorted by name. */
  private List<Entry> entries;

  @Getter
  @Setter
  @Accessors(chain = true)
  @Introspected
  public static class Entry {
    /** Full entry path inside the zip (e.g. {@code Snapshot/Terminology/sct2_Concept_…}). */
    private String name;
    /** Data rows excluding the header line. */
    private Long rowCount;
    /** Compressed entry size from the zip directory, or {@code null} when not exposed. */
    private Long compressedSize;
  }
}
