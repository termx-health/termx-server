package org.termx.snomed.integration.rf2.scan;

import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Singleton;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.termx.snomed.rf2.SnomedImportRequest;
import org.termx.snomed.rf2.SnomedRF2Upload;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class SnomedRF2UploadCacheService {
  private static final int RETENTION_DAYS = 7;

  private final SnomedRF2UploadCacheRepository repository;

  @Transactional
  public SnomedRF2Upload save(SnomedImportRequest request, String filename, byte[] zipData) {
    SnomedRF2Upload upload = new SnomedRF2Upload()
        .setBranchPath(request.getBranchPath())
        .setRf2Type(request.getType())
        .setCreateCodeSystemVersion(request.isCreateCodeSystemVersion())
        .setFilename(filename)
        .setZipSize(zipData == null ? 0L : (long) zipData.length)
        .setZipData(zipData)
        .setImported(false)
        .setStarted(OffsetDateTime.now());
    upload.setId(repository.save(upload));
    return upload;
  }

  @Transactional
  public void attachLorqueId(Long uploadId, Long lorqueId) {
    repository.setScanLorqueId(uploadId, lorqueId);
  }

  public SnomedRF2Upload load(Long id) {
    return repository.load(id);
  }

  @Transactional
  public void markImported(Long id) {
    repository.markImported(id);
  }

  public void cleanup(int daysOld) {
    repository.cleanup(daysOld);
  }

  @Scheduled(fixedDelay = "6h", initialDelay = "10m")
  public void scheduledCleanup() {
    try {
      repository.cleanup(RETENTION_DAYS);
    } catch (Exception e) {
      log.error("Failed to cleanup snomed_rf2_upload cache", e);
    }
  }
}
