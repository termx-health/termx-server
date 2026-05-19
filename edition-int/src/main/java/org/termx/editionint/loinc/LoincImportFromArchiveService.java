package org.termx.editionint.loinc;

import com.kodality.commons.exception.NotFoundException;
import jakarta.inject.Singleton;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.termx.bob.BobObject;
import org.termx.bob.BobObjectService;
import org.termx.core.sys.job.logger.ImportLogger;
import org.termx.editionint.loinc.utils.LoincImportFromArchiveRequest;
import org.termx.editionint.loinc.utils.LoincZipReader;
import org.termx.sys.job.JobLogResponse;

/**
 * Drives LOINC release import against an archive already stored in the {@code "loinc"} Bob
 * container. The release zip is streamed Minio → local temp file (heap-safe even on large
 * releases); we then unpack the eight known CSV entries by basename via {@link LoincZipReader}
 * and feed the existing {@link LoincService#importLoinc(Map)} pipeline as a background job.
 */
@Slf4j
@Singleton
@RequiredArgsConstructor
public class LoincImportFromArchiveService {
  private final BobObjectService bobObjectService;
  private final LoincService loincService;
  private final ImportLogger importLogger;

  public JobLogResponse startImport(LoincImportFromArchiveRequest req) {
    BobObject archive = requireArchive(req.getArchiveUuid());

    Path tempFile;
    try {
      tempFile = Files.createTempFile("loinc-import-", ".zip");
    } catch (Exception e) {
      throw new RuntimeException("Failed to create temp file for LOINC import: " + e.getMessage(), e);
    }
    List<Pair<String, byte[]>> files;
    try (InputStream is = bobObjectService.loadContentStream(archive)) {
      Files.copy(is, tempFile, StandardCopyOption.REPLACE_EXISTING);
      // Unpack from the temp file — the LOINC pipeline still needs byte[] per CSV, but each
      // CSV is small (tens of MB) compared to the full zip, so peak heap is one CSV at a time
      // rather than the whole archive.
      try (InputStream unpackIn = Files.newInputStream(tempFile)) {
        files = new LoincZipReader().unpack(unpackIn, req.getLanguage());
      }
    } catch (Exception e) {
      try {
        Files.deleteIfExists(tempFile);
      } catch (Exception ignored) {}
      throw new RuntimeException("Failed to download / unpack LOINC archive '" + req.getArchiveUuid() + "': " + e.getMessage(), e);
    } finally {
      try {
        Files.deleteIfExists(tempFile);
      } catch (Exception ignored) {}
    }

    if (files.isEmpty()) {
      throw new IllegalArgumentException("LOINC archive '" + req.getArchiveUuid() + "' does not contain any recognised CSV files (expected Part.csv, LoincPartLink_Primary.csv, etc.)");
    }

    Map<String, Object> params = Map.of("request", req.toImportRequest(), "files", files);
    return importLogger.runJob(LoincController.JOB_TYPE, params, loincService::importLoinc);
  }

  private BobObject requireArchive(String uuid) {
    if (uuid == null || uuid.isBlank()) {
      throw new IllegalArgumentException("archiveUuid is required");
    }
    BobObject archive = bobObjectService.load(uuid);
    if (archive == null) {
      throw new NotFoundException("Archive '" + uuid + "' not found in Bob");
    }
    if (archive.getStorage() == null || !LoincBobContainerAuthorizer.CONTAINER.equals(archive.getStorage().getContainer())) {
      throw new IllegalArgumentException("Archive '" + uuid + "' is not in the LOINC container");
    }
    return archive;
  }
}
