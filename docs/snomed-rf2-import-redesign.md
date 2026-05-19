# SNOMED RF2 import redesign

**Status:** Draft for review · No implementation yet
**Authors:** TermX team
**Related code:** [`snomed/`](../snomed/), [`bob/`](../bob/), [`termx-core/sys/lorque/`](../termx-core/src/main/java/org/termx/core/sys/lorque/)

## Context

Uploading a full SNOMED CT International edition through the UI ("Import from RF2") crashes the server with `java.lang.OutOfMemoryError: Java heap space`. The stack trace lands in Micronaut's reactive HTTP pipeline before the import even reaches Snowstorm.

The crash is not a Snowstorm or RF2 problem — it's a TermX-side architecture problem:

1. The controller materializes the ~1 GB ZIP into a `byte[]` in JVM heap before doing anything.
2. The parser (used by the dry-run scan flow) further accumulates every concept / description / relationship row in `List<…>` fields in heap.
3. The dev-server container runs with `-Xmx1800m`, which has no realistic chance of holding all of that plus normal request traffic.

Bumping heap or catching `OutOfMemoryError` is a band-aid. This doc proposes the durable fix: stream uploads to Minio, do all heavy lifting in a background Lorque job, and use the official IHTSDO `delta-generator-tool` so we push the smallest possible payload to Snowstorm.

## Current architecture

```
  UI ──upload──▶ SnomedController @Post /snomed/imports
                  │
                  │  blockingGet() — entire ZIP → byte[] in heap
                  ▼
              SnomedService.importRF2File
                  │
                  │  byte[] → MultipartBodyPublisher
                  ▼
              SnowstormClient (two-step: createImportJob → uploadRF2File)
                  │
                  ▼
                Snowstorm
```

Reference points:
- `snomed/src/main/java/org/termx/snomed/integration/SnomedController.java:252–267` — the upload endpoint, with `FileUtil.readBytes(Flowable.fromPublisher(file).firstOrError().blockingGet())` at line 255.
- `snomed/src/main/java/org/termx/snomed/integration/SnomedService.java:123–149` — chains `snowstormClient.createImportJob(req).join()` then `uploadRF2File(jobId, importFile).join()`.
- `snomed/src/main/java/org/termx/snomed/client/SnowstormClient.java:157–175` — already exposes the two-step Snowstorm import API.
- `snomed/src/main/java/org/termx/snomed/integration/rf2/scan/SnomedRF2UploadCacheService.java:21–32` — caches uploaded bytes as a **DB blob**; cleanup every 6 hours, 7-day retention.
- `snomed/src/main/java/org/termx/snomed/integration/rf2/scan/SnomedRF2ZipParser.java:45–59` — file-name patterns (`sct2_Concept_*`, `sct2_Description_*`, `sct2_TextDefinition_*`, `sct2_Relationship_*` / `sct2_StatedRelationship_*`, `der2_cRefset_Language*`).

## Proposed architecture

```
  UI ──upload──▶ SnomedController (streamed)
                  │  ① stream multipart → temp file on disk
                  │  ② upload temp file → Minio bucket
                  │  ③ create LorqueProcess(status=RUNNING)
                  │  ④ kick off virtual-thread job, return processId immediately
                  ▼
                                                ┌────────────────────────┐
              SnomedRF2ImportJob (async) ───────│  Minio                 │
                  │                              │  snomed-imports/      │
                  │  download new + baseline    │   <edition>/<eff>/    │
                  │   from Minio → /tmp         │     <filename>.zip    │
                  │                              └────────────────────────┘
                  │  invoke delta-generator-tool
                  │   (subprocess on /tmp/new + /tmp/baseline → /tmp/delta.zip)
                  │
                  │  SnowstormClient.createImportJob → Location
                  │  SnowstormClient.uploadRF2File(jobId, delta.zip)
                  │
                  │  lorqueProcessService.complete(processId, …)
                  ▼
                Snowstorm

  UI ──poll──▶ LorqueProcessController GET /lorque-processes/{id}/status
```

## Key decisions

### D1. Where uploads land — Minio, not DB blob, not memory

**Decision:** Stream the multipart upload to a temp file on disk inside the controller, then `MinioService.store(...)` it under `snomed-imports/<edition-id>/<effective-time>/<original-filename>.zip`. Delete the temp file once uploaded. Remove the DB-blob caching path entirely (or repurpose it for metadata only).

**Why:**
- Minio already wraps this — `bob/src/main/java/org/termx/bob/minio/MinioService.java:35–108` (`store`, `retrieve`, `delete`).
- Configuration already wired — `bob.minio.url` / `access-key` / `secret-key` in `MinioClientFactory`.
- Auto-creates the bucket on first write.
- Confirmed running in dev and prod.

### D2. Heavy work moves to a Lorque async job

**Decision:** Mirror the pattern already used by `SnomedRF2ScanService` (the dry-run scan endpoint). The controller returns a `LorqueProcess` immediately; a virtual-thread runs the actual import; status flows through `lorqueProcessService.start/reportProgress/complete/fail`.

**Why:**
- Pattern is already established — see `snomed/src/main/java/org/termx/snomed/integration/rf2/scan/SnomedRF2ScanService.java:39–66` and `termx-core/src/main/java/org/termx/core/sys/lorque/LorqueProcessService.java:39–72`.
- UI already polls `GET /lorque-processes/{id}/status` (controller line 24).
- OOM-class failures land on a background thread, away from the HTTP pipeline. Even if something goes wrong, the user gets a job-failed status, not a dropped connection.

### D3. delta-generator-tool runs as a subprocess

**Decision:** Invoke the IHTSDO `delta-generator-tool` jar via `ProcessBuilder`. Ship the jar in the Docker image (multi-stage Dockerfile: stage one downloads / builds the jar; stage two copies it). The Java service runs it with `--input-snapshot=<baseline.zip> --input-snapshot=<new.zip> --output-delta=<out.zip>` (exact CLI to be confirmed against the tool's README).

**Alternative considered:** add as a Maven dependency. **Rejected because:** the tool isn't published to Maven Central in a form we control, its own dependency tree is large, and subprocess gives us clean process isolation — an OOM in the delta computation doesn't bring down the TermX JVM.

**Risks:**
- No existing subprocess pattern in the codebase (greenfield).
- Need to surface stdout/stderr into the Lorque process's progress / failure messages.
- Need a strict timeout; delta generation on a full International edition is minutes, not seconds, but should not be unbounded.

### D4. Baseline retrieval

**Decision:** Look up the most-recent successful import for the same edition (same `<edition-id>`) from the `lorque_process` table (or a small new `snomed_import` table — see Q1 below), download that ZIP from Minio to `/tmp`, and use it as the baseline for delta-generator. If no baseline exists (first-ever import for the edition), skip the delta step and import the full snapshot directly via the same Snowstorm two-step.

### D5. The two-step Snowstorm flow stays — already correct

The `SnowstormClient` already does `createImportJob(req)` → returns Location header → `uploadRF2File(jobId, …)`. No change needed there, except the input becomes a `Path` or `InputStream` instead of `byte[]` so the JVM never holds the full archive (the delta archive is smaller, but on principle).

Reference: `snomed/src/main/java/org/termx/snomed/client/SnowstormClient.java:157–175`.

### D6. Retention

- Per-edition: keep the most recent **N** raw uploads in Minio (default `N=3`). Older ones get GC'd by a scheduled job mirroring the existing 6-hour cleanup loop in `SnomedRF2UploadCacheService:53–54`.
- Delta archives (intermediate): delete from `/tmp` after the Snowstorm upload completes — they're regenerable.

## Open questions

| # | Question | Notes |
| --- | --- | --- |
| Q1 | New `snomed_import` table or extend `lorque_process`? | A dedicated table makes baseline lookup ("most recent succeeded import for edition X") trivial and survives Lorque cleanup. Recommend new table; keep Lorque as the *job-status* mechanism only. |
| Q2 | Exact `delta-generator-tool` CLI shape? | Confirm against [github.com/IHTSDO/delta-generator-tool](https://github.com/IHTSDO/delta-generator-tool) README. Best done by whoever picks up the implementation. |
| Q3 | Should `SnomedRF2UploadCacheService` (DB-blob cache for the dry-run scan) move to Minio too? | Probably yes for consistency, but out-of-scope for this PR. Track separately. |
| Q4 | Authorization on the Minio bucket | Read/write from TermX server only, or also presigned URLs for direct browser → Minio uploads (would skip TermX as proxy entirely)? Presigned is more work but eliminates one more "JVM holds the file" path. Recommend deferring to Phase 3. |
| Q5 | What happens if delta-generator fails / produces empty delta? | If empty → no-op, mark Lorque complete with "no changes". If failure → fail Lorque with the subprocess stderr captured. |

## Phased delivery

Three PRs, deployable independently. Each leaves the system in a better state than before; later phases build on earlier ones.

### Phase 1 — Stream upload to Minio, route `/imports` through Lorque

Smallest unit of work that eliminates the OOM. Does **not** introduce delta-generator yet.

- `SnomedController.import(...)` rewritten to stream the multipart body to a temp file (no `blockingGet()`, no full `byte[]`), then `MinioService.store(...)`, then start a Lorque job, then return `LorqueProcess`.
- New `SnomedRF2ImportJob` (or extend `SnomedService`) that downloads from Minio to `/tmp`, calls `SnowstormClient.createImportJob` → `uploadRF2File`, reports progress, completes/fails the Lorque process.
- New table (if Q1 is "yes"): `snomed_import` with columns `id, edition_id, effective_time, filename, minio_key, lorque_process_id, status, created_at`.
- UI: switch the "Import from RF2" button to start polling the Lorque status the same way `/imports/scan` already does.

Acceptance: International edition (~1 GB) imports successfully on dev-server with current `-Xmx1800m`; heap usage stays bounded; failure cases (Snowstorm down, malformed ZIP) surface as `lorqueProcessService.fail` instead of HTTP 500.

### Phase 2 — Wire delta-generator-tool

Builds on Phase 1's job framework.

- Add `delta-generator-tool` jar to the `termx-app` Docker image (multi-stage: download the released jar, copy into final image).
- New `DeltaGeneratorRunner` service that takes two `Path`s (baseline, new) and a target output `Path`, runs the jar via `ProcessBuilder`, streams stderr to logs, times out at configurable threshold.
- `SnomedRF2ImportJob` gains a delta step between download and Snowstorm upload: look up baseline, run delta-generator, push delta. First-ever-import path skips delta.

Acceptance: a second import of the same edition (with concept changes between) uploads a delta archive that is dramatically smaller than the source ZIP, and Snowstorm shows the expected diff applied.

### Phase 3 — Presigned upload URLs (optional / nice-to-have)

UI uploads directly to Minio via a presigned URL; TermX server receives only the object key. Eliminates the last "TermX proxies a 1 GB file" path. Could be folded into Phase 1 if we want, but tracking separately because it touches the UI and adds Minio policy considerations.

## Out of scope for this design

- Refactoring the existing dry-run scan (`/imports/scan`) — it already uses Lorque, just from a DB-blob cache rather than Minio. Migration tracked via Q3.
- Memory-efficient parsing inside `SnomedRF2ZipParser` — once delta-generator is doing the heavy lifting, the scan's in-memory accumulators stop mattering for the import path. Keep scan as-is.
- Changes to other terminology imports (FHIR CodeSystem, etc.) — different code paths, different scale.

## Verification plan (when implementation lands)

1. Local: `JAVA_OPTS=-Xmx1800m` (current dev-server) + International edition import → completes without OOM. (Phase 1 alone.)
2. Local: second import of the same edition with one concept changed → delta archive is < 1 MB; Snowstorm shows only that change. (Phase 2.)
3. Failure injection: kill Snowstorm mid-import → Lorque process moves to FAILED with the connection error in `result_text`; UI shows it.
4. Failure injection: feed delta-generator a corrupted ZIP → subprocess exits non-zero; Lorque captures stderr and marks FAILED.
5. Container memory: peak RSS during a full import stays under 2 GB.
6. Minio retention: trigger 4 imports for the same edition; oldest 1 is deleted by the cleanup job.
