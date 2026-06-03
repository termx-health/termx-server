package org.termx.snomed.integration;

import com.kodality.commons.exception.NotFoundException;
import com.kodality.commons.util.JsonUtil;
import jakarta.inject.Singleton;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.termx.bob.BobObject;
import org.termx.bob.BobObjectService;
import org.termx.core.auth.SessionStore;
import org.termx.core.sys.lorque.LorqueProcessService;
import org.termx.core.utils.VirtualThreadExecutor;
import org.termx.snomed.client.SnowstormClient;
import org.termx.snomed.integration.rf2.scan.SnomedRF2ScanService;
import org.termx.snomed.rf2.SnomedImportFromArchiveRequest;
import org.termx.snomed.rf2.SnomedImportRequest;
import org.termx.snomed.rf2.SnomedImportTracking;
import org.termx.sys.lorque.LorqueProcess;
import org.termx.sys.lorque.ProcessResult;

/**
 * Drives the SNOMED RF2 import / dry-run-scan flow against an archive that already lives in the
 * {@code "snomed"} Bob container. The archive bytes are streamed Minio → Snowstorm (or Minio →
 * temp file for the scan), so the JVM never holds the whole zip — the customer-reported OOM on
 * full International editions does not apply to this path.
 */
@Slf4j
@Singleton
@RequiredArgsConstructor
public class SnomedRF2ImportFromArchiveService {
  private static final String PROCESS_NAME_IMPORT = "snomed-rf2-import";
  private static final String PROCESS_NAME_SCAN = "snomed-rf2-scan";

  private final BobObjectService bobObjectService;
  private final SnowstormClient snowstormClient;
  private final SnomedImportTrackingRepository trackingRepository;
  private final LorqueProcessService lorqueProcessService;
  private final SnomedRF2ScanService scanService;
  // Used to send an email when the upload-stage fails (catch block below) — Snowstorm
  // never received the file, so no SnomedImportTracking row gets written and the
  // pollPendingImports sweep can't pick it up. The polling service exposes
  // notifyUploadFailure() that builds the same email shape from a synthetic tracking.
  private final SnomedImportPollingService snomedImportPollingService;

  /**
   * Kick off an async import from a Bob-stored RF2 zip. Returns immediately with a {@link
   * LorqueProcess} the UI can poll; the actual work runs on a virtual thread.
   */
  public LorqueProcess startImport(SnomedImportFromArchiveRequest req) {
    BobObject archive = requireArchive(req.getArchiveUuid());
    LorqueProcess process = lorqueProcessService.start(new LorqueProcess().setProcessName(PROCESS_NAME_IMPORT));

    SnomedImportRequest importRequest = req.toImportRequest();
    Long processId = process.getId();

    log.info("snomed-rf2-import: queued import processId={} archiveUuid={} file={} branch={} type={}",
        processId, req.getArchiveUuid(),
        archive.getStorage() == null ? null : archive.getStorage().getFilename(),
        importRequest.getBranchPath(), importRequest.getType());

    CompletableFuture.runAsync(SessionStore.wrap(() -> {
      // Lifecycle log lines for the post-import email — same pattern as LoincService's
      // ImportLog.successes. Captured into SnomedImportTracking.details when we save the
      // row; polling appends one more line on terminal status.
      java.util.List<String> details = new java.util.ArrayList<>();
      long phaseStart = System.currentTimeMillis();
      try {
        lorqueProcessService.reportProgress(processId, 5, "creating Snowstorm import job");
        String jobId = snowstormClient.createImportJob(importRequest).join();
        long createMs = System.currentTimeMillis() - phaseStart;
        details.add(String.format("Created Snowstorm import job %s on branch %s (type %s) in %d ms",
            jobId, importRequest.getBranchPath(), importRequest.getType(), createMs));
        log.info("snomed-rf2-import: {}", details.getLast());

        lorqueProcessService.reportProgress(processId, 20, "uploading archive (streaming)");
        long uploadStart = System.currentTimeMillis();
        snowstormClient.uploadRF2File(jobId, () -> bobObjectService.loadContentStream(archive)).join();
        long uploadMs = System.currentTimeMillis() - uploadStart;
        details.add(String.format("Streamed archive (Bob uuid %s) to Snowstorm in %d ms",
            req.getArchiveUuid(), uploadMs));
        log.info("snomed-rf2-import: {}", details.getLast());

        details.add("Snowstorm processing started; awaiting polling-based completion notification");
        trackingRepository.save(new SnomedImportTracking()
            .setSnowstormJobId(jobId)
            .setBranchPath(importRequest.getBranchPath())
            .setType(importRequest.getType())
            .setStatus("RUNNING")
            .setStarted(OffsetDateTime.now())
            .setNotified(false)
            .setDetails(details));
        lorqueProcessService.reportProgress(processId, 100, "Snowstorm job " + jobId);
        lorqueProcessService.complete(processId, ProcessResult.text(JsonUtil.toJson(Map.of("jobId", jobId))));
      } catch (Exception e) {
        log.error("SNOMED RF2 from-archive import failed for archive {}", req.getArchiveUuid(), e);
        lorqueProcessService.fail(processId, ProcessResult.text(ExceptionUtils.getMessage(e) + "\n" + ExceptionUtils.getStackTrace(e)));
        // Snowstorm never received the file (or never confirmed receipt), so the polling
        // sweep won't notify recipients. Fire an upload-stage failure email here.
        snomedImportPollingService.notifyUploadFailure(
            importRequest.getBranchPath(), req.getArchiveUuid(), ExceptionUtils.getMessage(e));
      }
    }), VirtualThreadExecutor.get());

    return process;
  }

  /**
   * Kick off an async dry-run scan from a Bob-stored RF2 zip. The archive is streamed Minio →
   * local temp file (never fully buffered in heap), then the parser reads it via {@link
   * InputStream}. The cache row records the Bob UUID so "Proceed with import" can re-stream
   * straight from Bob → Snowstorm without materialising the bytes.
   */
  public LorqueProcess startScan(SnomedImportFromArchiveRequest req) {
    BobObject archive = requireArchive(req.getArchiveUuid());
    Path tempFile;
    try {
      tempFile = Files.createTempFile("snomed-scan-", ".zip");
    } catch (Exception e) {
      throw new RuntimeException("Failed to create temp file for SNOMED scan: " + e.getMessage(), e);
    }
    try (InputStream is = bobObjectService.loadContentStream(archive)) {
      Files.copy(is, tempFile, StandardCopyOption.REPLACE_EXISTING);
    } catch (Exception e) {
      try {
        Files.deleteIfExists(tempFile);
      } catch (Exception ignored) {}
      throw new RuntimeException("Failed to spool archive '" + req.getArchiveUuid() + "' from Bob: " + e.getMessage(), e);
    }
    String filename = archive.getStorage() == null ? null : archive.getStorage().getFilename();
    // scanRF2FromBob deletes the temp file when the async parse completes.
    return scanService.scanRF2FromBob(req.toImportRequest(), tempFile, filename, req.getArchiveUuid());
  }

  private BobObject requireArchive(String uuid) {
    if (uuid == null || uuid.isBlank()) {
      throw new IllegalArgumentException("archiveUuid is required");
    }
    BobObject archive = bobObjectService.load(uuid);
    if (archive == null) {
      throw new NotFoundException("Archive '" + uuid + "' not found in Bob");
    }
    if (archive.getStorage() == null || !SnomedBobContainerAuthorizer.CONTAINER.equals(archive.getStorage().getContainer())) {
      throw new IllegalArgumentException("Archive '" + uuid + "' is not in the SNOMED container");
    }
    return archive;
  }
}
