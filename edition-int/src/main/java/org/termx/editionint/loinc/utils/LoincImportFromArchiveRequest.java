package org.termx.editionint.loinc.utils;

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

  public LoincImportRequest toImportRequest() {
    return new LoincImportRequest().setVersion(version).setLanguage(language);
  }
}
