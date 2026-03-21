package org.termx.terminology.fileimporter.codesystem;

import com.kodality.commons.db.transaction.TransactionManager;
import com.kodality.commons.model.Issue;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.termx.terminology.ApiError;
import org.termx.terminology.terminology.codesystem.CodeSystemImportService;
import org.termx.terminology.terminology.codesystem.compare.CodeSystemCompareService;
import org.termx.terminology.terminology.codesystem.version.CodeSystemVersionService;
import org.termx.ts.codesystem.CodeSystem;
import org.termx.ts.codesystem.CodeSystemCompareResult;
import org.termx.ts.codesystem.CodeSystemImportAction;
import org.termx.ts.codesystem.CodeSystemVersion;
import org.termx.ts.codesystem.CodeSystemVersionQueryParams;
import org.termx.ts.association.AssociationType;

/**
 * Runs "validate data only" (dry-run) import in a <strong>separate</strong> transaction so that
 * {@link TransactionManager#rollback()} only rolls back the temporary shadow import, and does not mark the
 * caller's Spring transaction as rollback-only (which caused {@code UnexpectedRollbackException} on commit).
 */
@Slf4j
@Singleton
@RequiredArgsConstructor
public class CodeSystemFileImportDryRunService {

  private final CodeSystemImportService codeSystemImportService;
  private final CodeSystemVersionService codeSystemVersionService;
  private final CodeSystemCompareService codeSystemCompareService;

  /**
   * Imports a shadow copy, compares versions, then rolls back this transaction only.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public DryRunResult dryRunImportCompareAndRollback(
      CodeSystem shadowCopy,
      List<AssociationType> associationTypes,
      CodeSystemImportAction action,
      String reqCodeSystemId,
      CodeSystemVersion existingCodeSystemVersion) {
    try {
      codeSystemImportService.importCodeSystem(shadowCopy, associationTypes, action);
    } catch (Exception e) {
      log.warn("Dry-run shadow import failed", e);
      TransactionManager.rollback();
      return new DryRunResult(null, Optional.of(ApiError.TE716.toIssue(Map.of("exception", ExceptionUtils.getMessage(e)))));
    }

    Long sourceVersionId = existingCodeSystemVersion != null ? existingCodeSystemVersion.getId() : null;
    log.info("\tUsing the source version id '{}'", sourceVersionId);

    String shadowVersionCode = shadowCopy.getVersions().getFirst().getVersion();
    log.info("\tFinding the target version by version code '{}'", shadowVersionCode);
    Long targetVersionId = findVersion(reqCodeSystemId, shadowVersionCode)
        .map(CodeSystemVersion::getId)
        .orElseThrow(() -> new IllegalStateException("Shadow version not found after dry-run import"));

    log.info("\tComparing two versions: '{}' and '{}'", sourceVersionId, targetVersionId);
    CodeSystemCompareResult compare = codeSystemCompareService.compare(sourceVersionId, targetVersionId);
    String diff = CodeSystemImportCompareDiffFormatter.composeCompareSummary(compare);

    TransactionManager.rollback();
    return new DryRunResult(diff, Optional.empty());
  }

  private Optional<CodeSystemVersion> findVersion(String csId, String version) {
    CodeSystemVersionQueryParams params = new CodeSystemVersionQueryParams();
    params.setVersion(version);
    params.setCodeSystem(csId);
    params.setLimit(1);
    return codeSystemVersionService.query(params).findFirst();
  }

  public record DryRunResult(String diff, Optional<Issue> importError) {}
}
