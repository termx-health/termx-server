package org.termx.snomed.rf2;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SnomedImportRequest {
  private String branchPath;
  private boolean createCodeSystemVersion;
  private String type;
  private boolean dryRun;
  /** Scope for the dry-run scan. "summary" (default) parses only concepts + descriptions
   *  (and text-definitions). "full" additionally parses relationships and the language refset
   *  so attributes and acceptability are included in the report. Ignored for non-dry-run imports. */
  private String mode;
}
