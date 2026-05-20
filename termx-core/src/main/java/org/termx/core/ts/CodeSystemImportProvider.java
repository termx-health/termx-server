package org.termx.core.ts;

import org.termx.ts.codesystem.CodeSystemImportRequest;
import org.termx.ts.codesystem.CodeSystemImportSummary;

public abstract class CodeSystemImportProvider {

  /**
   * Imports a code system from a {@link CodeSystemImportRequest} and returns a brief
   * summary the caller can fold into a job-log entry. Returning a value (vs. the previous
   * {@code void}) was added so admin email notifications can say "N concepts imported"
   * instead of an empty success list — see {@link CodeSystemImportSummary} for fields.
   *
   * <p>Callers that don't need the summary (the simpler ICD-10 / ATC / Orphanet / Ichi-UZ
   * import services) can ignore the return; Java permits discarding a non-void result.
   */
  public abstract CodeSystemImportSummary importCodeSystem(CodeSystemImportRequest request);
}
