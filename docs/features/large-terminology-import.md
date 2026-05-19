# Large-Terminology Import (SNOMED + LOINC)

**Status:** Planned · Design doc: [`docs/snomed-rf2-import-redesign.md`](../snomed-rf2-import-redesign.md) · Not yet implemented.

## Description

This feature lets a terminology manager upload large terminology archives — full SNOMED CT International edition (~1 GB RF2 zip) or full LOINC release-bundle — through the TermX UI without crashing the server. Heavy work runs as a background Lorque job; the UI polls a status endpoint and shows progress until the import either completes against Snowstorm / the LOINC importer or fails with a clear diagnostic.

For SNOMED specifically, if a previous successful import of the same edition is stored, TermX computes the delta against it using the IHTSDO [`delta-generator-tool`](https://github.com/IHTSDO/delta-generator-tool) and pushes only the delta to Snowstorm — dramatically smaller payload, much faster import, lower memory pressure end to end.

It replaces the previous synchronous upload flow that buffered the entire archive into the JVM heap (`FileUtil.readBytes(... .blockingGet())`) and caused `OutOfMemoryError` on dev-server with `-Xmx1800m`.

**Key capabilities**

- Streamed multipart upload — bytes go from the network straight to a temp file on disk, then to Minio. The JVM never holds the full archive.
- Archive storage in Minio under a stable key: `terminology-archives/<terminology>/<edition>/<effective-time>/<original-filename>`. Bucket retention defaults to the most recent **N=3** archives per edition; older ones are reclaimed by a scheduled cleanup job.
- Async import via the existing Lorque job framework. UI returns immediately with a `LorqueProcess` id and polls `/lorque-processes/{id}/status` for progress (same pattern the dry-run scan already uses).
- **SNOMED:** delta-against-previous-version import when a baseline exists in Minio. First-ever import of an edition pushes the full snapshot.
- **LOINC:** streamed-full-import — same upload/Minio/Lorque path, no delta tool (there isn't one for LOINC).
- *Uploads* sub-resource (`/imports/uploads`) for both terminologies — admins can store archives in Minio without triggering an import, list everything currently stored, trigger an import from any stored archive, and delete archives manually. Required so the SNOMED delta path has baselines, and useful in its own right for recovery / audit.
- *Stored uploads* UI component — a new list view in the SNOMED and LOINC pages shows everything in Minio with filters (edition, status, effective time), per-row *Trigger import* and *Delete* actions, and an *Upload archive (no import)* button for seeding baselines.
- All failures (Snowstorm unavailable, malformed archive, `delta-generator-tool` non-zero exit) land as `lorqueProcessService.fail(...)` with the underlying error in `result_text`, never as an HTTP 500 or a dropped connection.

## Configuration

### Properties

| Property | Env variable | Default | Description |
|---|---|---|---|
| `bob.minio.url` | `BOB_MINIO_URL` | (deployment) | Minio endpoint. Already used by [`bob/`](../../bob/). |
| `bob.minio.access-key` / `secret-key` | `BOB_MINIO_ACCESS_KEY` / `BOB_MINIO_SECRET_KEY` | (deployment) | Minio credentials. |
| `termx.terminology-archive.bucket` | — | `terminology-archives` | Minio bucket name; auto-created on first write. |
| `termx.terminology-archive.retention-per-edition` | — | `3` | How many most-recent archives to keep per edition; older soft-deleted by the cleanup job. |
| `termx.snomed.delta-generator.jar` | `SNOMED_DELTA_GENERATOR_JAR` | `/opt/delta-generator-tool.jar` | Path to the `delta-generator-tool` jar inside the container. |
| `termx.snomed.delta-generator.timeout-seconds` | — | `1800` (30 min) | Hard cap on the delta-generator subprocess. |
| `snowstorm.url` / `snowstorm.user` / `snowstorm.password` | — | (deployment) | Snowstorm endpoint + Basic Auth. Unchanged from current. |
| `micronaut.server.max-request-size` | — | `1610612736` (1.5 GB) | Hard cap on multipart upload size; SNOMED International is ~1 GB and growing. |

### Privileges

| Privilege | Used for |
|---|---|
| `snomed-ct.CodeSystem.write` | Trigger SNOMED import; upload, list, or delete entries in *Stored uploads*. |
| `loinc.CodeSystem.write` | Trigger LOINC import; upload, list, or delete entries in *Stored uploads*. |
| `snomed-ct.CodeSystem.read` / `loinc.CodeSystem.read` | View import job status, list archives. |

### Storage layout

```
terminology-archives/
├── snomed/
│   └── international/
│       ├── 20260101/
│       │   └── SnomedCT_InternationalRF2_PRODUCTION_20260101T120000Z.zip
│       └── 20260401/
│           └── SnomedCT_InternationalRF2_PRODUCTION_20260401T120000Z.zip
└── loinc/
    └── international/
        └── 2.79/
            └── Loinc_2.79.zip
```

Archives are referenced from the `terminology_import` table (columns `id, terminology, edition_id, effective_time, filename, minio_key, lorque_process_id, status, created_at`). Status values:

| Status | Meaning |
|---|---|
| `UPLOADED` | Archive sits in Minio, no Snowstorm/LOINC processing happened. Created by `POST /imports/uploads`. |
| `RUNNING` | Lorque job is currently importing this archive. |
| `COMPLETED` | Import finished successfully. |
| `FAILED` | Import failed. See the associated `LorqueProcess.result_text` for the error. |

### Retention

The cleanup job (`TerminologyArchiveCleanupService.scheduledCleanup`, schedule mirroring the existing dry-run-scan cleanup — every 6 h, initial delay 10 min) keeps the most recent `termx.terminology-archive.retention-per-edition` archives per (terminology, edition) tuple in Minio. `terminology_import` rows are kept indefinitely as historical metadata; only the Minio objects are deleted.

## Use-Cases

### Scenario 1: Import the latest SNOMED International edition

A terminology manager has the new quarterly RF2 zip and a previous edition already imported.

1. Navigates to the SNOMED CodeSystem edit page → opens *Import from RF2*.
2. Picks the file → clicks *Confirm*. The browser streams the upload; the server writes it to a temp file and pushes it to Minio under `terminology-archives/snomed/international/<effectiveTime>/...`.
3. The endpoint returns a `LorqueProcess` immediately. UI switches to a progress modal polling `/lorque-processes/{id}/status`.
4. Background job downloads the new archive + the previous baseline from Minio to `/tmp`, runs `delta-generator-tool` to produce `/tmp/delta.zip`, then pushes the delta through the Snowstorm two-step (`createImportJob` → `uploadRF2File`).
5. Progress updates land at each phase (`stored to Minio`, `computing delta`, `pushing to Snowstorm`).
6. On success, modal closes; the new `terminology_import` row has `status=COMPLETED`. If Snowstorm errors out, the job is marked `FAILED` with the response body in `result_text`.

### Scenario 2: First-ever import of a new SNOMED edition

The terminology manager is bootstrapping a fresh deployment, no previous archives in Minio.

Same flow as Scenario 1 with one difference: the job sees no baseline for the edition in `terminology_import`, skips `delta-generator-tool`, and pushes the full snapshot to Snowstorm. From the user's perspective the only visible change is progress text reading `pushing full snapshot to Snowstorm` instead of `computing delta`.

### Scenario 3: Import the latest LOINC release

LOINC has no delta-generator equivalent; this path is always full-import.

1. Navigates to the LOINC CodeSystem edit page → opens *Import LOINC release*.
2. Picks the file → clicks *Confirm*. Same streamed upload → Minio path as SNOMED.
3. Returns `LorqueProcess` id; UI polls.
4. Background job downloads from Minio to `/tmp`, hands the file path to `LoincService.importLoinc(...)` (reading from disk, not a `byte[]`).
5. Progress updates as the CSV parser advances; success / failure routes through the same Lorque mechanism.

### Scenario 4: Seed a baseline for an already-imported edition

A deployment has been running for months with a SNOMED International edition imported pre-redesign; the archive isn't in Minio yet, so the next normal import would skip the delta step (no baseline available).

1. Admin grabs the original zip from their archive (the file IHTSDO published).
2. Opens *Stored uploads* in the SNOMED page and clicks *Upload archive (no import)*.
3. Picks the file and fills in `edition=international`, `effectiveTime=20260101`. UI calls `POST /snomed/imports/uploads`.
4. Endpoint streams the upload → Minio, writes a `terminology_import` row with `status=UPLOADED`. **Nothing is pushed to Snowstorm.** The new row appears in the *Stored uploads* table.
5. The next normal SNOMED import (Scenario 1) finds the uploaded row as its baseline and computes a delta against it.

LOINC seeding works the same way through `POST /loinc/imports/uploads` and the LOINC *Stored uploads* page.

### Scenario 5: Browse and manage stored uploads

An operator wants to audit what's currently in Minio, free up an old archive, or re-import from a stored archive without re-uploading the file.

1. Opens *Stored uploads* in the SNOMED page → table lists every `terminology_import` row for SNOMED. Columns: *Edition*, *Effective time*, *Filename*, *Status*, *Size*, *Stored at*.
2. Filters by status (`UPLOADED`, `COMPLETED`, `RUNNING`, `FAILED`) and by edition.
3. Per row:
    - *Trigger import* — calls `POST /snomed/imports?fromUploadId={id}` to start a Lorque job against the stored Minio object, no new upload required. Useful for retrying a failed import after fixing Snowstorm.
    - *Delete* — calls `DELETE /snomed/imports/uploads/{id}`. Soft-deletes the row and removes the Minio object. Prompts for confirmation if the row is the most-recent baseline for an edition.

The LOINC *Stored uploads* page works identically.

### Scenario 6: Snowstorm crashes mid-import

A long-running SNOMED import is in progress when Snowstorm becomes unreachable.

1. The Lorque job's Snowstorm push fails. `lorqueProcessService.fail(processId, ProcessResult.text("Snowstorm error: <body>"))` is called.
2. UI polling sees `status=FAILED` and shows the recorded error text.
3. Operator restarts Snowstorm. They re-trigger the import; because the archive is already in Minio, the system could (future enhancement) offer a "retry without re-uploading" — for now, the operator re-runs the same flow and the archive is overwritten in Minio (or skipped if file hash matches).
4. TermX JVM heap is unaffected — the failure stays in the background thread.

## API surface (new)

| Method + path | Purpose | Returns |
|---|---|---|
| `POST /snomed/imports` *(replacing existing)* | Trigger SNOMED import. Multipart file OR `?fromUploadId={id}` query param to import an already-stored archive. | `LorqueProcess` (HTTP 202) |
| `POST /snomed/imports/uploads` | Store an archive in Minio without importing; form fields `edition`, `effectiveTime` + multipart file. | `terminology_import` row |
| `GET /snomed/imports/uploads` | List stored archives. Query params: `?edition=…&effectiveTime=…&status=…`. | `terminology_import[]` |
| `GET /snomed/imports/uploads/{id}` | Single archive metadata. | `terminology_import` |
| `DELETE /snomed/imports/uploads/{id}` | Remove the row + Minio object. | 204 No Content |
| `POST /loinc/imports` *(replacing existing)* | LOINC equivalent of the SNOMED import endpoint. | `LorqueProcess` |
| `POST /loinc/imports/uploads` | LOINC equivalent of the SNOMED uploads endpoint. | `terminology_import` row |
| `GET /loinc/imports/uploads` | List LOINC stored archives. | `terminology_import[]` |
| `DELETE /loinc/imports/uploads/{id}` | Remove a LOINC stored archive. | 204 No Content |
| `GET /lorque-processes/{id}/status` *(existing)* | Poll job status. | `{status, progress, note}` |
| `GET /lorque-processes/{id}` *(existing)* | Full job record incl. `result_text` on failure. | `LorqueProcess` |

The synchronous upload paths that buffer everything in heap are **removed**, not deprecated. There is no fallback to the old behavior.

## UI surface (new)

A new *Stored uploads* component lives on both the SNOMED and LOINC CodeSystem pages. It renders the result of `GET /<terminology>/imports/uploads` as a filterable, paginated table:

- **Columns:** Edition · Effective time · Filename · Status · Size · Stored at · Actions
- **Filters:** Edition (free text), Effective time (range), Status (`UPLOADED` / `RUNNING` / `COMPLETED` / `FAILED`)
- **Per-row actions:**
  - *Trigger import* — only visible when row status is `UPLOADED` or `FAILED`. Calls `POST /<terminology>/imports?fromUploadId={id}` to start an import job against the already-stored Minio object — no new upload.
  - *View job* — visible when the row has a `lorque_process_id`. Routes to the job-status modal already used by the dry-run scan.
  - *Delete* — calls `DELETE /<terminology>/imports/uploads/{id}`. Confirmation prompt; warns if the row is the most-recent baseline for an edition (deleting it means the next SNOMED import skips the delta step).
- **Top-of-page actions:**
  - *Upload archive (no import)* — opens a small modal: file picker + `edition` + `effectiveTime`. Calls `POST /<terminology>/imports/uploads`.
  - *Import new release* — opens the existing import modal (the streamed-upload flow described in Scenarios 1–3).

## Out of scope for the initial rollout

- Refactoring the dry-run scan (`/snomed/imports/scan`) to also use Minio storage — it already works async via Lorque from a DB-blob cache; migration tracked in the design doc as Q3.
- Memory-efficient parsing inside `SnomedRF2ZipParser` — once `delta-generator-tool` does the heavy lifting on disk, the scan's in-memory accumulators don't affect the import path.
- Other terminology imports (FHIR CodeSystem upload, UCUM, …) — different code paths, different scale; re-evaluate per-terminology if OOM reports appear.
- Presigned upload URLs (browser → Minio direct, skipping TermX as proxy) — tracked as a Phase 4 follow-up in the design doc.

## Related

- Design doc: [`docs/snomed-rf2-import-redesign.md`](../snomed-rf2-import-redesign.md)
- Existing async pattern this mirrors: [`docs/features/snomed-rf2-dry-run-scan.md`](snomed-rf2-dry-run-scan.md)
- Minio wrapper used: [`bob/.../MinioService.java`](../../bob/src/main/java/org/termx/bob/minio/MinioService.java)
- IHTSDO delta-generator-tool: <https://github.com/IHTSDO/delta-generator-tool>
