package org.termx.terminology.fhir.conformance;

import io.micronaut.core.util.StringUtils;
import jakarta.inject.Singleton;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.termx.bob.BobObject;
import org.termx.bob.BobObjectService;
import org.termx.core.sys.lorque.LorqueProcessService;
import org.termx.sys.lorque.LorqueProcess;
import org.termx.sys.lorque.ProcessResult;
import org.termx.ts.conformance.TxConformanceRunRequest;

/**
 * Orchestrates a tx-ecosystem conformance run as an async {@link LorqueProcess} (process name
 * {@code tx-conformance}); the FHIR {@code TestReport} is stored as the process result (JSON), so
 * callers fetch status/results via {@code GET /lorque-processes/{id}} — same pattern as SNOMED
 * import-from-archive. A custom test bundle (optional {@code archiveUuid}) is streamed out of the
 * {@code tx-conformance} Bob container to a temp file and passed to the runner as {@code -input}.
 */
@Slf4j
@Singleton
@RequiredArgsConstructor
public class TxConformanceService {
  private final TxConformanceRunner runner;
  private final LorqueProcessService lorqueProcessService;
  private final BobObjectService bobObjectService;

  public LorqueProcess run(TxConformanceRunRequest request) {
    return lorqueProcessService.run("tx-conformance", request, req -> {
      Path bundle = null;
      try {
        if (StringUtils.isNotEmpty(req.getArchiveUuid())) {
          bundle = downloadBundle(req.getArchiveUuid());
        }
        String report = runner.run(req, bundle);
        return new ProcessResult().setContent(report.getBytes(StandardCharsets.UTF_8)).setContentType("application/json");
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RuntimeException("tx conformance run interrupted", e);
      } catch (Exception e) {
        throw new RuntimeException(e.getMessage(), e);
      } finally {
        if (bundle != null) {
          try {
            Files.deleteIfExists(bundle);
          } catch (Exception e) {
            log.warn("failed to delete temp test bundle {}", bundle, e);
          }
        }
      }
    });
  }

  private Path downloadBundle(String uuid) throws Exception {
    BobObject obj = bobObjectService.load(uuid);
    if (obj == null) {
      throw new IllegalArgumentException("test bundle not found: " + uuid);
    }
    if (obj.getStorage() == null || !TxConformanceBobContainerAuthorizer.CONTAINER.equals(obj.getStorage().getContainer())) {
      throw new IllegalArgumentException("archive " + uuid + " is not in the '" + TxConformanceBobContainerAuthorizer.CONTAINER + "' container");
    }
    Path tmp = Files.createTempFile("txtests-bundle-", ".bundle");
    try (InputStream is = bobObjectService.loadContentStream(obj)) {
      Files.copy(is, tmp, StandardCopyOption.REPLACE_EXISTING);
    }
    log.info("downloaded custom test bundle {} ({} bytes) for tx conformance run", uuid, Files.size(tmp));
    return tmp;
  }
}
