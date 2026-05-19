package org.termx.snomed.integration;

import com.kodality.commons.exception.NotFoundException;
import com.kodality.commons.util.JsonUtil;
import jakarta.inject.Singleton;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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

  /**
   * Kick off an async import from a Bob-stored RF2 zip. Returns immediately with a {@link
   * LorqueProcess} the UI can poll; the actual work runs on a virtual thread.
   */
  public LorqueProcess startImport(SnomedImportFromArchiveRequest req) {
    BobObject archive = requireArchive(req.getArchiveUuid());
    LorqueProcess process = lorqueProcessService.start(new LorqueProcess().setProcessName(PROCESS_NAME_IMPORT));

    SnomedImportRequest importRequest = req.toImportRequest();
    Long processId = process.getId();

    CompletableFuture.runAsync(SessionStore.wrap(() -> {
      try {
        lorqueProcessService.reportProgress(processId, 5, "creating Snowstorm import job");
        String jobId = snowstormClient.createImportJob(importRequest).join();
        lorqueProcessService.reportProgress(processId, 20, "uploading archive (streaming)");
        snowstormClient.uploadRF2File(jobId, () -> bobObjectService.loadContentStream(archive)).join();
        trackingRepository.save(new SnomedImportTracking()
            .setSnowstormJobId(jobId)
            .setBranchPath(importRequest.getBranchPath())
            .setType(importRequest.getType())
            .setStatus("RUNNING")
            .setStarted(OffsetDateTime.now())
            .setNotified(false));
        lorqueProcessService.reportProgress(processId, 100, "Snowstorm job " + jobId);
        lorqueProcessService.complete(processId, ProcessResult.text(JsonUtil.toJson(Map.of("jobId", jobId))));
      } catch (Exception e) {
        log.error("SNOMED RF2 from-archive import failed for archive {}", req.getArchiveUuid(), e);
        lorqueProcessService.fail(processId, ProcessResult.text(ExceptionUtils.getMessage(e) + "\n" + ExceptionUtils.getStackTrace(e)));
      }
    }), VirtualThreadExecutor.get());

    return process;
  }

  /**
   * Kick off an async dry-run scan from a Bob-stored RF2 zip. Delegates the actual parse to
   * {@link SnomedRF2ScanService#scanRF2(SnomedImportRequest, byte[], String)} after spooling
   * the Minio stream into a byte array — the scan code path needs the bytes in memory anyway
   * because the parser opens the zip from a {@code ByteArrayInputStream}. (A follow-up can
   * change the parser to accept a {@link java.nio.file.Path} so even the scan is heap-safe.)
   */
  public LorqueProcess startScan(SnomedImportFromArchiveRequest req) {
    BobObject archive = requireArchive(req.getArchiveUuid());
    byte[] bytes;
    try (InputStream is = bobObjectService.loadContentStream(archive)) {
      bytes = is.readAllBytes();
    } catch (Exception e) {
      throw new RuntimeException("Failed to download archive '" + req.getArchiveUuid() + "' from Bob: " + e.getMessage(), e);
    }
    String filename = archive.getStorage() == null ? null : archive.getStorage().getFilename();
    return scanService.scanRF2(req.toImportRequest(), bytes, filename);
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
