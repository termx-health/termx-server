package org.termx.snomed.integration;

import com.kodality.commons.exception.NotFoundException;
import com.kodality.commons.model.QueryResult;
import com.kodality.commons.util.JsonUtil;
import jakarta.inject.Singleton;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.termx.bob.BobObject;
import org.termx.bob.BobObjectQueryParams;
import org.termx.bob.BobObjectService;
import org.termx.bob.BobStorage;
import org.termx.core.auth.SessionStore;
import org.termx.core.sys.lorque.LorqueProcessService;
import org.termx.core.utils.VirtualThreadExecutor;
import org.termx.snomed.rf2.SnomedDeltaCalculateRequest;
import org.termx.sys.lorque.LorqueProcess;
import org.termx.sys.lorque.ProcessResult;

/**
 * Orchestrates the delta calculation flow: spool the two source archives Bob → local temp,
 * invoke {@link SnomedDeltaGeneratorRuntime} as a subprocess, upload the produced delta zip
 * back to Bob as a new BobObject with {@code meta.kind="delta"}, and return a
 * {@link LorqueProcess} the UI can poll. The process result text holds
 * {@code {"deltaUuid": "…", "rowsExported": N}} on success — Phase 2c's "Calculate Delta"
 * button uses {@code deltaUuid} to navigate to the new archive's detail page.
 *
 * <p>Also exposes {@link #findDiffCandidates(String)} — the source of the Diff section's
 * baseline picker. Peers are scoped by {@code meta.branchPath} (the user-requested behaviour
 * from Phase 2's design questions) and exclude the current archive plus any other delta
 * archives (you don't diff against a diff).</p>
 */
@Slf4j
@Singleton
@RequiredArgsConstructor
public class SnomedDeltaCalculateService {
  private static final String PROCESS_NAME = "snomed-rf2-delta";

  private final BobObjectService bobObjectService;
  private final LorqueProcessService lorqueProcessService;
  private final SnomedDeltaGeneratorRuntime runtime;

  // ─── Diff candidates ────────────────────────────────────────────────────────────────────

  public List<BobObject> findDiffCandidates(String currentUuid) {
    BobObject current = requireSnomedArchive(currentUuid);
    String branchPath = metaString(current, "branchPath");
    if (branchPath == null) {
      // Without a branchPath tag we can't safely scope — return empty rather than guess.
      return List.of();
    }
    BobObjectQueryParams params = new BobObjectQueryParams();
    params.setContainer(SnomedBobContainerAuthorizer.CONTAINER);
    params.setMeta(JsonUtil.toJson(Map.of("branchPath", branchPath)));
    params.all();
    QueryResult<BobObject> page = bobObjectService.query(params);
    return page.getData().stream()
        .filter(o -> !currentUuid.equals(o.getUuid()))
        .filter(o -> !"delta".equals(metaString(o, "kind")))
        .sorted(Comparator.comparing((BobObject o) -> {
          String n = o.getStorage() == null ? "" : o.getStorage().getFilename();
          return n == null ? "" : n;
        }))
        .toList();
  }

  // ─── Delta calculation ──────────────────────────────────────────────────────────────────

  /**
   * Spool both Bob archives to temp files, fork the delta-generator-tool, upload the produced
   * delta zip back to Bob, return a Lorque process the UI polls. The body of the lorque
   * "running" state advances 5 → 30 → 60 → 100 as we move through spooling, generator,
   * Bob upload, and completion.
   */
  public LorqueProcess startDeltaCalculation(String currentUuid, SnomedDeltaCalculateRequest req) {
    BobObject current = requireSnomedArchive(currentUuid);
    BobObject baseline = requireSnomedArchive(req.getBaselineUuid());
    if (currentUuid.equals(req.getBaselineUuid())) {
      throw new IllegalArgumentException("Baseline must be a different archive than the current one");
    }
    String currentBranch = metaString(current, "branchPath");
    String baselineBranch = metaString(baseline, "branchPath");
    if (currentBranch != null && baselineBranch != null && !currentBranch.equals(baselineBranch)) {
      throw new IllegalArgumentException("Baseline branchPath (" + baselineBranch + ") differs from current (" + currentBranch + ")");
    }
    // Fail fast on a non-forward delta. If the current edition is not newer than the baseline the
    // delta is empty, and the upstream delta-generator crashes (ArrayIndexOutOfBounds) instead of
    // producing an empty archive — so reject it here with a clear message rather than after a
    // multi-minute subprocess run. effectiveTime is YYYYMMDD, so a lexical compare is chronological.
    String currentEffectiveTime = metaString(current, "effectiveTime");
    String baselineEffectiveTime = metaString(baseline, "effectiveTime");
    if (currentEffectiveTime != null && baselineEffectiveTime != null
        && currentEffectiveTime.compareTo(baselineEffectiveTime) <= 0) {
      throw new IllegalArgumentException("Current edition effectiveTime (" + currentEffectiveTime
          + ") is not newer than the baseline (" + baselineEffectiveTime + "); the delta would be empty. "
          + "Select a newer edition as the current archive.");
    }
    // The Stored archives card filters by JSONB containment on {shortName, branchPath}, so
    // the delta has to carry both keys or it disappears from the card it should appear in.
    // Take shortName from the current archive (the new state, which has the meta tags
    // assigned by the modal upload), falling back to baseline if the current is missing it.
    // Assigned in a single expression so it stays effectively-final for the async lambda.
    final String currentShortName = metaString(current, "shortName") != null
        ? metaString(current, "shortName")
        : metaString(baseline, "shortName");
    // Captured for the description string — admins inspecting the delta in the Stored archives
    // list see "Delta of <baseline>.zip → <current>.zip" instead of a generic line.
    final String baselineFilename = current == null || baseline.getStorage() == null
        ? null : baseline.getStorage().getFilename();
    final String currentFilename = current.getStorage() == null
        ? null : current.getStorage().getFilename();

    LorqueProcess process = lorqueProcessService.start(new LorqueProcess().setProcessName(PROCESS_NAME));
    Long pid = process.getId();
    boolean latestState = req.isLatestState();

    CompletableFuture.runAsync(SessionStore.wrap(() -> {
      Path workDir = null;
      Path baselineTemp = null;
      Path currentTemp = null;
      try {
        workDir = Files.createTempDirectory("snomed-delta-");
        baselineTemp = workDir.resolve("baseline.zip");
        currentTemp = workDir.resolve("current.zip");

        lorqueProcessService.reportProgress(pid, 5, "spooling baseline archive");
        copyToTemp(baseline, baselineTemp);
        lorqueProcessService.reportProgress(pid, 15, "spooling current archive");
        copyToTemp(current, currentTemp);

        lorqueProcessService.reportProgress(pid, 30, "running delta-generator (this can take several minutes)");
        SnomedDeltaGeneratorRuntime.Result result = runtime.run(baselineTemp, currentTemp, workDir, latestState);

        lorqueProcessService.reportProgress(pid, 80, "uploading delta archive to Bob");
        String deltaUuid = persistDelta(
            currentUuid, req.getBaselineUuid(),
            currentShortName, currentBranch,
            baselineFilename, currentFilename,
            result, latestState);

        Map<String, Object> resultPayload = new LinkedHashMap<>();
        resultPayload.put("deltaUuid", deltaUuid);
        if (result.getRowsExported() != null) {
          resultPayload.put("rowsExported", result.getRowsExported());
        }
        resultPayload.put("durationMs", result.getDurationMs());
        resultPayload.put("latestState", latestState);

        lorqueProcessService.reportProgress(pid, 100, "done");
        lorqueProcessService.complete(pid, ProcessResult.text(JsonUtil.toJson(resultPayload)));
      } catch (Exception e) {
        log.error("Delta calculation failed (current={}, baseline={})", currentUuid, req.getBaselineUuid(), e);
        lorqueProcessService.fail(pid, ProcessResult.text(ExceptionUtils.getMessage(e) + "\n" + ExceptionUtils.getStackTrace(e)));
      } finally {
        deleteQuietly(baselineTemp);
        deleteQuietly(currentTemp);
        deleteTreeQuietly(workDir);
      }
    }), VirtualThreadExecutor.get());

    return process;
  }

  // ─── Helpers ────────────────────────────────────────────────────────────────────────────

  private void copyToTemp(BobObject source, Path target) throws Exception {
    try (InputStream in = bobObjectService.loadContentStream(source)) {
      Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private String persistDelta(String sourceUuid, String baselineUuid,
                              String shortName, String branchPath,
                              String baselineFilename, String currentFilename,
                              SnomedDeltaGeneratorRuntime.Result result, boolean latestState) {
    Map<String, Object> meta = new LinkedHashMap<>();
    meta.put("kind", "delta");
    // shortName must be present for the per-CS Stored archives card's JSONB @> filter to
    // include this delta. Same for branchPath — both are inherited from the source archive.
    if (shortName != null) {
      meta.put("shortName", shortName);
    }
    if (branchPath != null) {
      meta.put("branchPath", branchPath);
    }
    meta.put("sourceUuid", sourceUuid);
    meta.put("baselineUuid", baselineUuid);
    // Filenames are stored on meta too — handy for downstream callers that don't want to do a
    // second Bob lookup just to render "baseline.zip → current.zip" labels.
    if (baselineFilename != null) {
      meta.put("baselineFilename", baselineFilename);
    }
    if (currentFilename != null) {
      meta.put("sourceFilename", currentFilename);
    }
    meta.put("generatedAt", OffsetDateTime.now().toString());
    meta.put("latestState", latestState);
    if (result.getRowsExported() != null) {
      meta.put("rowsExported", result.getRowsExported());
    }

    // Description format: "Delta of <baseline>.zip → <current>.zip — N rows".
    // Both filename slots are best-effort; fall back to a generic sentence if either is missing.
    StringBuilder desc = new StringBuilder();
    if (baselineFilename != null && currentFilename != null) {
      desc.append("Delta of ").append(baselineFilename)
          .append(" → ").append(currentFilename);
    } else {
      desc.append("Delta generated by IHTSDO DeltaGeneratorTool 3.0.0");
    }
    if (result.getRowsExported() != null) {
      desc.append(" — ").append(result.getRowsExported()).append(" rows");
    }

    // Critical: route every delta to its own Minio key. The delta-generator-tool always
    // writes its output to a fixed name ("Delta.zip"), so if we forward that name unchanged
    // every delta upload writes to `snomed:/Delta.zip` and overwrites whatever the previous
    // calculation produced. The Bob DB then has multiple rows pointing at the same physical
    // zip — scans report identical stats because they're parsing the same bytes — reported
    // live as "two different scan-result URLs show exactly the same information".
    //
    // Per-upload random path keeps each delta isolated and reuses the existing Minio cleanup
    // semantics (delete by Bob uuid removes only that one Minio object). The date suffix on
    // the filename is purely cosmetic — admins downloading the delta zip from the UI get a
    // human-readable name like Delta-2026-05-20.zip rather than the upstream "Delta.zip".
    String storagePath = "/delta-" + java.util.UUID.randomUUID() + "/";
    String dateSuffix = java.time.LocalDate.now().toString(); // YYYY-MM-DD per ISO-8601
    String storageFilename = "Delta-" + dateSuffix + ".zip";
    BobObject draft = new BobObject()
        .setContentType("application/zip")
        .setDescription(desc.toString())
        .setMeta(meta)
        .setStorage(new BobStorage()
            .setContainer(SnomedBobContainerAuthorizer.CONTAINER)
            .setPath(storagePath)
            .setFilename(storageFilename));
    return bobObjectService.store(draft, result.getDeltaZip());
  }

  private BobObject requireSnomedArchive(String uuid) {
    if (uuid == null || uuid.isBlank()) {
      throw new IllegalArgumentException("archive uuid is required");
    }
    BobObject o = bobObjectService.load(uuid);
    if (o == null) {
      throw new NotFoundException("Archive '" + uuid + "' not found in Bob");
    }
    if (o.getStorage() == null || !SnomedBobContainerAuthorizer.CONTAINER.equals(o.getStorage().getContainer())) {
      throw new IllegalArgumentException("Archive '" + uuid + "' is not in the SNOMED container");
    }
    // The delta-generator expects RF2 zips. Without this check a non-RF2 object in the SNOMED
    // container (e.g. a JSON nomenclature file) reaches the tool, which then finds 0 components and
    // crashes in createArchive. Reject it here with the offending filename/content-type.
    if (!isRf2Archive(o.getStorage().getFilename(), o.getContentType())) {
      throw new IllegalArgumentException("Archive '" + uuid + "' is not an RF2 zip (filename='"
          + o.getStorage().getFilename() + "', contentType='" + o.getContentType()
          + "'). The SNOMED delta requires RF2 .zip archives for both the baseline and the current edition.");
    }
    return o;
  }

  /** An RF2 archive is a zip — accept when either the filename ends in .zip or the content-type says zip. */
  static boolean isRf2Archive(String filename, String contentType) {
    boolean zipName = filename != null && filename.toLowerCase().endsWith(".zip");
    boolean zipType = contentType != null && contentType.toLowerCase().contains("zip");
    return zipName || zipType;
  }

  private static String metaString(BobObject o, String key) {
    if (o == null || o.getMeta() == null) {
      return null;
    }
    Object v = o.getMeta().get(key);
    return v == null ? null : v.toString();
  }

  private static void deleteQuietly(Path p) {
    if (p == null) {
      return;
    }
    try {
      Files.deleteIfExists(p);
    } catch (Exception ignored) {
    }
  }

  private static void deleteTreeQuietly(Path dir) {
    if (dir == null) {
      return;
    }
    try (Stream<Path> s = Files.walk(dir)) {
      s.sorted(Comparator.reverseOrder()).forEach(SnomedDeltaCalculateService::deleteQuietly);
    } catch (Exception ignored) {
    }
  }
}
