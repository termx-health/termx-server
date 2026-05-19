package org.termx.snomed.rf2;

import lombok.Getter;
import lombok.Setter;

/**
 * Trigger SNOMED RF2 import or dry-run scan against an archive that already lives in the
 * {@code "snomed"} Bob container (uploaded earlier via {@code POST /bob/objects}). The archive
 * bytes never have to be re-uploaded, and the JVM never holds the whole zip — the import
 * service streams it from Minio.
 */
@Getter
@Setter
public class SnomedImportFromArchiveRequest {
  /** UUID of the {@code bob.object} row that holds the RF2 archive. */
  private String archiveUuid;
  private String branchPath;
  private String type;
  private boolean createCodeSystemVersion;
  /** Scan-only: "summary" (default) or "full". Ignored on the real import endpoint. */
  private String mode;

  public SnomedImportRequest toImportRequest() {
    SnomedImportRequest r = new SnomedImportRequest();
    r.setBranchPath(branchPath);
    r.setType(type);
    r.setCreateCodeSystemVersion(createCodeSystemVersion);
    r.setMode(mode);
    return r;
  }
}
