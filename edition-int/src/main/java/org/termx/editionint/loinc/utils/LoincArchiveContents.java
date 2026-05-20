package org.termx.editionint.loinc.utils;

import io.micronaut.core.annotation.Introspected;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Response shape for {@code GET /loinc/archives/{uuid}/files} — drives the per-slot select
 * boxes on the LOINC import page. Each entry is a {@code .csv} file inside the zip; the
 * {@code suggestedSlot} is what the auto-dispatch (basename match in {@link LoincZipReader})
 * would assign — {@code null} means "not a known LOINC file, admin can still pick it
 * manually for any slot but it's not pre-selected anywhere".
 */
@Getter
@Setter
@Accessors(chain = true)
@Introspected
public class LoincArchiveContents {
  private List<Entry> entries;
  /**
   * Version string extracted from the {@code Loinc_<version>_DifferenceReport.pdf} entry
   * the LOINC release zips ship at their root. {@code null} when the archive doesn't
   * follow that convention. Used by the import page as a fallback when the archive's
   * outer filename / {@code meta.version} doesn't already carry the version.
   */
  private String detectedVersion;

  @Getter
  @Setter
  @Accessors(chain = true)
  @Introspected
  public static class Entry {
    /** Full entry name inside the zip (normalised with forward slashes). */
    private String name;
    /** {@code parts | terminology | supplementary-properties | panels | answer-list |
     *  answer-list-link | order-observation | translations}, or {@code null} when the auto-
     *  dispatch couldn't classify this entry. */
    private String suggestedSlot;
  }
}
