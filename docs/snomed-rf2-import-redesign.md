# Large-terminology import redesign (SNOMED + LOINC)

**Status:** Draft for review · No implementation yet
**Authors:** TermX team
**Related code:** [`snomed/`](../snomed/), [`edition-int/loinc/`](../edition-int/src/main/java/org/termx/editionint/loinc/), [`bob/`](../bob/), [`termx-core/sys/lorque/`](../termx-core/src/main/java/org/termx/core/sys/lorque/)

## Context

Uploading a full SNOMED CT International edition through the UI ("Import from RF2") crashes the server with `java.lang.OutOfMemoryError: Java heap space`. The stack trace lands in Micronaut's reactive HTTP pipeline before the import even reaches Snowstorm.

**The same pattern affects LOINC import** (`edition-int/.../LoincController.java:62`) — `FileUtil.readBytes(Flowable.fromPublisher(file).firstOrError().blockingGet())` — and the same fix applies.

The crash is not a Snowstorm / RF2 / LOINC problem — it's a TermX-side architecture problem:

1. The controller materializes the multi-hundred-MB upload into a `byte[]` in JVM heap before doing anything.
2. For SNOMED, the parser (used by the dry-run scan flow) further accumulates every concept / description / relationship row in `List<…>` fields in heap.
3. The dev-server container runs with `-Xmx1800m`, which has no realistic chance of holding all of that plus normal request traffic.

Bumping heap or catching `OutOfMemoryError` is a band-aid. This doc proposes the durable fix:

- **Both SNOMED and LOINC** — stream uploads to Minio and do all heavy lifting in a background Lorque job.
- **SNOMED specifically** — use the official IHTSDO `delta-generator-tool` so we push only the diff against the previous version to Snowstorm, not the full snapshot.
- **No fallback to the current synchronous upload path** — the existing flow is fundamentally OOM-prone and is being replaced, not kept around as a backup.

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

Reference points (SNOMED):
- `snomed/src/main/java/org/termx/snomed/integration/SnomedController.java:252–267` — the upload endpoint, with `FileUtil.readBytes(Flowable.fromPublisher(file).firstOrError().blockingGet())` at line 255.
- `snomed/src/main/java/org/termx/snomed/integration/SnomedService.java:123–149` — chains `snowstormClient.createImportJob(req).join()` then `uploadRF2File(jobId, importFile).join()`.
- `snomed/src/main/java/org/termx/snomed/client/SnowstormClient.java:157–175` — already exposes the two-step Snowstorm import API.
- `snomed/src/main/java/org/termx/snomed/integration/rf2/scan/SnomedRF2UploadCacheService.java:21–32` — caches uploaded bytes as a **DB blob**; cleanup every 6 hours, 7-day retention.
- `snomed/src/main/java/org/termx/snomed/integration/rf2/scan/SnomedRF2ZipParser.java:45–59` — file-name patterns (`sct2_Concept_*`, `sct2_Description_*`, `sct2_TextDefinition_*`, `sct2_Relationship_*` / `sct2_StatedRelationship_*`, `der2_cRefset_Language*`).

Reference points (LOINC):
- `edition-int/src/main/java/org/termx/editionint/loinc/LoincController.java:34–62` — same `byte[]`-in-heap pattern. The CSV parser at `LoincService` reads from a `ByteArrayInputStream`, so the entire archive sits in heap exactly the way SNOMED does.

## Proposed architecture

The diagram below shows the SNOMED case. **LOINC follows the same shape with one difference:** there is no equivalent of the IHTSDO delta-generator tool for LOINC, so the LOINC job downloads the new archive from Minio and pushes it straight to the LOINC importer (`LoincService`) — no delta computation. Everything else — streamed upload, Minio storage, Lorque async, status polling — is identical and shared.

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

## Resolved during review

- **Generalize across terminologies.** The storage + async-job foundation (Phase 1) is shared between SNOMED and LOINC. The delta-generator-tool integration (Phase 3) stays SNOMED-specific because the IHTSDO tool only understands RF2; LOINC has no equivalent delta tool and is handled as a streamed-full-import job.
- **Skip delta-generator when no baseline exists.** First-ever import of an edition (or LOINC version) imports the full snapshot directly. Subsequent imports look up the previous version from Minio and run delta-generator.
- **No fallback to the current synchronous upload.** The existing `/imports` and LOINC `/import` are being replaced, not kept alongside. The current path is the bug; preserving it as a fallback just preserves the bug.
- **delta-generator-tool runs as a subprocess** (D3). Tool is officially SNOMED-supported; subprocess gives clean OOM isolation and avoids dragging its full dependency tree into TermX's classpath.
- **Resource naming: `uploads`, not `backfill`.** What was called the "backfill" endpoint in the first draft is modeled as an `/imports/uploads` sub-resource: `POST /snomed/imports/uploads` (and `/loinc/imports/uploads`) to store, `GET /snomed/imports/uploads` to list, `DELETE …/{id}` to remove. Matches the existing `SnomedRF2Upload` entity name in the codebase. Better than "backfill" because it isn't an awkward verb-as-noun and naturally supports list/get/delete operations for the new UI component.
- **Q6 — Backfill source: admin re-uploads the original archive.** The admin always has the source file (they imported it once already), so a re-upload through `POST /snomed/imports/uploads` is universal, predictable, and requires zero migration code on the server. The alternative (reading from `SnomedRF2UploadCacheService` DB-blob cache) only covers archives uploaded in the last 7 days, can't be relied on, and would block on database I/O — rejected.

## Open questions

| # | Question | Notes |
| --- | --- | --- |
| Q1 | New `snomed_import` table or extend `lorque_process`? | A dedicated table makes baseline lookup ("most recent succeeded import for edition X") trivial and survives Lorque cleanup. Recommend new table (probably generalized as `terminology_import` to also cover LOINC); keep Lorque as the *job-status* mechanism only. |
| Q2 | Exact `delta-generator-tool` CLI shape? | Confirm against [github.com/IHTSDO/delta-generator-tool](https://github.com/IHTSDO/delta-generator-tool) README. Best done by whoever picks up the implementation. |
| Q3 | Should `SnomedRF2UploadCacheService` (DB-blob cache for the dry-run scan) move to Minio too? | Probably yes for consistency, but out-of-scope for this redesign. Track separately. |
| Q4 | Authorization on the Minio bucket | Read/write from TermX server only, or also presigned URLs for direct browser → Minio uploads (would skip TermX as proxy entirely)? Presigned is more work but eliminates one more "JVM holds the file" path. Deferred to Phase 4. |
| Q5 | What happens if delta-generator fails / produces empty delta? | If empty → no-op, mark Lorque complete with "no changes". If failure → fail Lorque with the subprocess stderr captured. |

## Phased delivery

Four PRs, deployable independently. Each leaves the system in a better state than before; later phases build on earlier ones.

### Phase 1 — Streamed upload + Minio + Lorque, for both SNOMED and LOINC

Smallest unit of work that eliminates the OOM. Does **not** introduce delta-generator yet — SNOMED imports a full snapshot just like before, just streamed through disk + Minio + a Lorque async job. LOINC's only fix is this phase; the rest doesn't apply to it.

A new shared building block:
- `TerminologyArchiveService` (in `termx-core` or a new shared module) wraps `MinioService` with a stable object-key convention: `terminology-archives/<terminology>/<edition>/<effective-time>/<original-filename>`. `terminology` is `snomed` or `loinc`. Used by both SNOMED and LOINC paths.
- New table `terminology_import` (resolves Q1): `id, terminology, edition_id, effective_time, filename, minio_key, lorque_process_id, status, created_at`. One row per import attempt; queried as "most recent succeeded for this edition" when Phase 2 lands.

Per-terminology changes:
- **SNOMED** — `SnomedController.import(...)` rewritten to stream the multipart body to a temp file (no `blockingGet()`, no full `byte[]`), `TerminologyArchiveService.store(...)` it, start a Lorque job (`SnomedRF2ImportJob`), return the `LorqueProcess`. The job downloads from Minio to `/tmp`, calls `SnowstormClient.createImportJob` → `uploadRF2File` reading from a `Path`, reports progress, completes/fails Lorque. Old synchronous code path deleted.
- **LOINC** — `LoincController.import(...)` (`edition-int/.../LoincController.java:34`) gets the same streamed-to-disk + Lorque-async refactor. The CSV parser already reads from an `InputStream`, so wiring it to a file path is mechanical. Old synchronous code path deleted.
- **UI** — both buttons switch to start polling `/lorque-processes/{id}/status` the same way `/imports/scan` already does (the polling helper exists).

Acceptance: International SNOMED edition (~1 GB) imports successfully on dev-server with current `-Xmx1800m`; LOINC release-bundle imports the same way; heap usage stays bounded; failure cases (Snowstorm down, malformed archive) surface as `lorqueProcessService.fail` instead of HTTP 500.

### Phase 2 — Uploads API + UI list

Small, depends only on Phase 1's `TerminologyArchiveService` + `terminology_import` table. Useful in its own right (recovery / audit / manual baseline seeding) and a prerequisite for Phase 3 to be effective — delta-generator needs a baseline already in Minio.

**Server endpoints** (one set per terminology, same shape):
- `POST /snomed/imports/uploads` — multipart file + form fields `edition`, `effectiveTime`. Streams the upload to Minio, records a `terminology_import` row with `status=UPLOADED`. Does **not** push anything to Snowstorm.
- `GET /snomed/imports/uploads` — list stored archives for the terminology. Supports query params `?edition=…&effectiveTime=…&status=…` for filtering. Returns one entry per `terminology_import` row (id, terminology, edition, effective time, filename, minio key, status, created at, optional Lorque process id).
- `GET /snomed/imports/uploads/{id}` — single archive metadata.
- `DELETE /snomed/imports/uploads/{id}` — soft-delete the `terminology_import` row and remove the Minio object. Used to free up baseline slots before the retention job runs.
- Same four endpoints under `/loinc/imports/uploads`.

Implementation reuses Phase 1's streamed-upload + `TerminologyArchiveService.store` plumbing; the only difference vs. a normal import is that the controller stops after the Minio store, never starts a Lorque job, and never calls Snowstorm.

**UI — Stored uploads list view.** A new Angular component (`stored-uploads-list`) renders the result of `GET …/uploads` as a table with columns *Edition*, *Effective time*, *Filename*, *Status*, *Size*, *Stored at*, *Actions*. Filters on terminology (SNOMED / LOINC), edition, status. Per-row actions: `Delete`, `Trigger import from this archive` (calls a new `POST /snomed/imports?fromUploadId={id}` shortcut that skips the upload step and starts the Lorque job directly against the stored Minio object). An *Upload archive (no import)* button on the page hits the `POST …/uploads` endpoint, useful for seeding baselines.

Acceptance: admin uploads the SNOMED archive that matches what is already in Snowstorm/TermX today via the UI; the row appears in the stored-uploads list with `status=UPLOADED`; subsequent normal import for the next release sees it as the baseline (after Phase 3). Deleting an entry removes the Minio object.

### Phase 3 — SNOMED delta-generator-tool integration

Builds on Phases 1 and 2.

- Add `delta-generator-tool` jar to the `termx-app` Docker image (multi-stage: download the released jar from IHTSDO releases, copy into the final image).
- New `DeltaGeneratorRunner` service that takes two `Path`s (baseline, new) and a target output `Path`, runs the jar via `ProcessBuilder`, streams stderr to logs, has a configurable timeout, and captures the exit code into the Lorque `result_text` on failure.
- `SnomedRF2ImportJob` gains a delta step between download and Snowstorm upload:
  - Look up the most-recent succeeded baseline for the edition (from `terminology_import`).
  - If none → push the full snapshot (Phase 1 behaviour).
  - If found → download baseline + new to `/tmp`, run delta-generator → `/tmp/delta.zip`, push the delta archive via Snowstorm two-step.
- Same job framework + UI; no surface change.

Acceptance: a second import of the same edition with one concept changed produces a delta archive dramatically smaller than the source ZIP, and Snowstorm shows the expected diff applied. Empty delta is a clean no-op with Lorque marked complete + "no changes" message.

This phase is **SNOMED-only.** LOINC stays on Phase 1's full-snapshot model — no equivalent diff tool exists.

### Phase 4 — Presigned upload URLs (optional / nice-to-have)

UI requests a presigned PUT URL from TermX, browser uploads directly to Minio, TermX receives only the object key over a small JSON callback. Eliminates the last "TermX proxies a multi-hundred-MB file" path entirely. Applies to both SNOMED and LOINC. Could be folded into Phase 1 but tracked separately because it touches the UI and the Minio policy model.

## Out of scope for this design

- Refactoring the existing dry-run scan (`/imports/scan`) — it already uses Lorque, just from a DB-blob cache rather than Minio. Migration tracked via Q3.
- Memory-efficient parsing inside `SnomedRF2ZipParser` — once delta-generator is doing the heavy lifting on disk, the scan's in-memory accumulators stop mattering for the import path. Keep scan as-is.
- Changes to other terminology imports beyond SNOMED and LOINC (FHIR CodeSystem upload, UCUM, etc.) — different code paths, different scale. Re-evaluate per-terminology if OOM reports come in.
- Keeping the current synchronous `/imports` / LOINC `/import` as a fallback — the existing flow is the bug we're fixing, not a backup worth preserving.

## Verification plan (when implementation lands)

1. **Phase 1 — SNOMED.** Local: `JAVA_OPTS=-Xmx1800m` (current dev-server) + International edition import → completes without OOM, peak RSS stays under 2 GB.
2. **Phase 1 — LOINC.** Same: a full LOINC release-bundle import via the new endpoint completes without OOM under the same heap limits.
3. **Phase 1 — failure mode.** Kill Snowstorm mid-import → Lorque process moves to FAILED with the connection error in `result_text`; UI shows it. No process holding onto a stale `byte[]` in heap.
4. **Phase 2 — stored uploads.** Admin uploads the SNOMED archive that matches what's already in TermX today via `POST /snomed/imports/uploads` → row appears in `terminology_import` with `status=UPLOADED`; nothing pushed to Snowstorm; archive readable from Minio; `GET /snomed/imports/uploads` returns the row; UI list shows it; `DELETE …/{id}` removes both the row (soft-delete) and the Minio object.
5. **Phase 3 — delta path.** With a baseline present from Phase 2, a normal import of the next release produces a delta archive dramatically smaller than the source ZIP; Snowstorm shows only the expected diff applied.
6. **Phase 3 — empty delta.** Re-import an identical archive → delta-generator produces an empty delta → Lorque marked complete with "no changes" message; nothing pushed to Snowstorm.
7. **Phase 3 — failure mode.** Feed delta-generator a corrupted ZIP → subprocess exits non-zero; Lorque captures stderr and marks FAILED; TermX JVM heap unaffected.
8. **Minio retention.** Trigger 4 imports for the same edition; oldest 1 is deleted by the cleanup job (retention policy = N most recent per edition, default `N=3`).
