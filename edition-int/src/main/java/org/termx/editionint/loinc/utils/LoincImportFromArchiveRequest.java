package org.termx.editionint.loinc.utils;

import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Trigger a LOINC import against a release archive that already lives in the
 * {@code "loinc"} Bob container (uploaded via {@code POST /bob/objects}). The server unpacks
 * the zip by basename ({@code Part.csv}, {@code LoincPartLink_Primary.csv}, etc.) and feeds the
 * normal {@link org.termx.editionint.loinc.LoincService#importLoinc(java.util.Map)} pipeline.
 */
@Getter
@Setter
@Accessors(chain = true)
public class LoincImportFromArchiveRequest {
  /** UUID of the {@code bob.object} row holding the LOINC release zip. */
  private String archiveUuid;
  /** LOINC release version label (passed through as {@link LoincImportRequest#getVersion()}). */
  private String version;
  /** ISO 639 lowercase language code; selects {@code <lang>LinguisticVariant.csv}. Optional. */
  private String language;
  /**
   * Optional explicit slot → entry-name map. When the admin overrides which CSV inside the
   * archive maps to which import slot (via the per-slot selects on the LOINC import page),
   * this carries the picks. Keys are {@code parts}, {@code terminology}, {@code panels},
   * {@code answer-list}, {@code answer-list-link}, {@code supplementary-properties},
   * {@code order-observation}, {@code translations}. Values are the full zip entry names
   * (e.g. {@code AccessoryFiles/PartFile/Part.csv}).
   *
   * <p>When {@code null} or empty, the server falls back to the basename-match
   * auto-dispatch — same behaviour as before this field existed.</p>
   */
  private Map<String, String> fileMap;

  public LoincImportRequest toImportRequest() {
    return new LoincImportRequest().setVersion(version).setLanguage(language);
  }
}
