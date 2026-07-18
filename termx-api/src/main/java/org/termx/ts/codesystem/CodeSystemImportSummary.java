package org.termx.ts.codesystem;

import io.micronaut.core.annotation.Introspected;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Per-code-system result of one {@code CodeSystemImportProvider.importCodeSystem(...)} call.
 * Returned to callers so they can populate {@code ImportLog.successes} (the per-job log
 * surfaced in the email-notification template) with meaningful "what changed" lines instead
 * of an empty list.
 *
 * <p>The added / modified split is computed by the provider with a single bulk-lookup of
 * existing concept codes BEFORE the import runs — it adds about one round trip per import
 * versus the previous {@code void}-returning signature, which is negligible against the
 * minutes-long persistence step but lets the post-import email say:
 *
 * <pre>
 *   loinc-answer-list@2.82: 27 101 concepts (22 146 added, 4 955 updated)
 *   loinc-part@2.82:        74 087 concepts (74 087 added, 0 updated)
 *   loinc@2.82:            109 325 concepts (109 325 added, 0 updated)
 * </pre>
 *
 * <p>Callers that don't care about the return (icd10, atc, orphanet) can ignore it
 * — Java is happy with a discarded non-void result; no source change needed at those sites.
 */
@Getter
@Setter
@Accessors(chain = true)
@Introspected
public class CodeSystemImportSummary {
  /** The {@code code_system.id} the import targeted (e.g. {@code "loinc"}). */
  private String codeSystem;
  /** The {@code code_system_version.version} the import created or updated (e.g. {@code "2.82"}). */
  private String version;
  /** Total concepts in the import payload — sum of added + updated. */
  private Integer totalConcepts;
  /** Concepts whose {@code code} did NOT already exist in this code system before the import. */
  private Integer addedConcepts;
  /** Concepts whose {@code code} already existed — the import wrote a new
   *  {@code code_system_entity_version} row for them under the new {@code version}. */
  private Integer updatedConcepts;
}
