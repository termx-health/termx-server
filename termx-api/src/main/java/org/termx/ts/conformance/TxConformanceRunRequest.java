package org.termx.ts.conformance;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Request to run the HL7 FHIR terminology ("tx-ecosystem") conformance suite against this server's
 * own FHIR endpoint via the validator's {@code txTests} command.
 *
 * <p>All fields are optional. With none set, the full bundled suite runs. {@code archiveUuid} points
 * at a custom test bundle previously uploaded to the {@code tx-conformance} Bob container
 * (via {@code POST /bob/objects}); it is passed to the runner as an additional {@code -input} loader.
 */
@Getter
@Setter
@Accessors(chain = true)
public class TxConformanceRunRequest {
  /** Run only this suite (validator {@code -suite}). */
  private String suite;
  /** Filter test names (validator {@code -filter}). */
  private String filter;
  /** Test modes, e.g. {@code general} (validator {@code -mode}). */
  private String mode;
  /** UUID of a {@code bob.object} in the {@code tx-conformance} container holding a custom test bundle. */
  private String archiveUuid;
}
