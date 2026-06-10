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
- Storage is the existing `bob.object` / `bob.object_storage` schema — same backing tables and same Minio bucket the Wiki module already uses. New container values `snomed` and `loinc` sit alongside the existing `wiki` rows; no migration of existing data.
- Generic `/bob/objects` REST API for upload, list, get, patch, delete — built on `BobObjectService`. Per-container authz: `snomed` requires `snomed-ct.CodeSystem.*`, `loinc` requires `loinc.CodeSystem.*`, `wiki` keeps existing Wiki privileges. The UI consumes this directly.
- *Stored archives* UI component — a new list view in the SNOMED and LOINC pages renders `GET /bob/objects?container={terminology}` with filters (edition, status, effective time), per-row *Trigger import* and *Delete* actions, and a top-of-page *Upload archive (no import)* button for seeding baselines.
- All failures (Snowstorm unavailable, malformed archive, `delta-generator-tool` non-zero exit) land as `lorqueProcessService.fail(...)` with the underlying error in `result_text`, never as an HTTP 500 or a dropped connection.
- **Empty / non-forward delta.** If the current archive is not newer than the baseline, the delta is empty and the upstream `delta-generator-tool` crashes (`ArrayIndexOutOfBoundsException` in `createArchive`). TermX guards this on both sides: `startDeltaCalculation` rejects the request up front when `current.effectiveTime <= baseline.effectiveTime` (a clear `IllegalArgumentException`), and `SnomedDeltaGeneratorRuntime` detects the tool's "Latest versions collected for 0 components" log and fails with an actionable message instead of the raw stack trace.

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

Archives live in the existing `bob.object` + `bob.object_storage` tables. Each archive is one `BobObject` row with metadata in the JSONB `meta` column:

```json
{
  "terminology": "snomed",
  "edition": "international",
  "effectiveTime": "20260101",
  "filename": "SnomedCT_InternationalRF2_PRODUCTION_20260101T120000Z.zip",
  "importStatus": "COMPLETED",
  "lorqueProcessId": 12345
}
```

The corresponding `bob.object_storage` row holds `container="snomed"` (or `loinc`), `path="/<edition>/<effective-time>/"`, `filename="<original-filename>"`. Wiki rows use `container="wiki"` with their own `meta` schema (`page`, `fileName`) — the two coexist in the same table without conflict.

`meta.importStatus` values:

| Status | Meaning |
|---|---|
| *(absent)* | Archive was uploaded but no import has been triggered against it. The `POST /bob/objects` upload-only path leaves `importStatus` unset. |
| `RUNNING` | A Lorque job is currently importing this archive. `lorqueProcessId` points to the live job. |
| `COMPLETED` | Import finished successfully. The archive is eligible to be used as a baseline by the next SNOMED import. |
| `FAILED` | Import failed. See the associated `LorqueProcess.result_text` for the error. The archive is still in Minio and can be retried (`POST /snomed/imports?fromObjectId={uuid}`). |

### Retention

A new cleanup job (`BobObjectRetentionService.scheduledCleanup`, every 6 h, initial delay 10 min) keeps the most recent `termx.terminology-archive.retention-per-edition` archives per (container, `meta.edition`) tuple. Older `bob.object` rows are soft-deleted (`sys_status='C'`) and their Minio objects removed. The retention policy is scoped to `container in (snomed, loinc)`; the `wiki` container is not touched.

## Use-Cases

### Scenario 1: Import the latest SNOMED International edition

A terminology manager has the new quarterly RF2 zip and a previous edition already imported.

1. Navigates to the SNOMED CodeSystem edit page → opens *Import from RF2*.
2. Picks the file → clicks *Confirm*. The browser streams the upload; the server writes it to a temp file and pushes it to Minio under `terminology-archives/snomed/international/<effectiveTime>/...`.
3. The endpoint returns a `LorqueProcess` immediately. UI switches to a progress modal polling `/lorque-processes/{id}/status`.
4. Background job downloads the new archive + the previous baseline from Minio to `/tmp`, runs `delta-generator-tool` to produce `/tmp/delta.zip`, then pushes the delta through the Snowstorm two-step (`createImportJob` → `uploadRF2File`).
5. Progress updates land at each phase (`stored to Minio`, `computing delta`, `pushing to Snowstorm`).
6. On success, modal closes; the new `bob.object` row has `meta.importStatus=COMPLETED`. If Snowstorm errors out, the job is marked `FAILED` with the response body in `result_text` and `meta.importStatus=FAILED`.

### Scenario 2: First-ever import of a new SNOMED edition

The terminology manager is bootstrapping a fresh deployment, no previous archives in Minio.

Same flow as Scenario 1 with one difference: the job queries `BobObjectService` for the most-recent `meta.importStatus=COMPLETED` row in `container=snomed` with matching `meta.edition`, finds none, skips `delta-generator-tool`, and pushes the full snapshot to Snowstorm. From the user's perspective the only visible change is progress text reading `pushing full snapshot to Snowstorm` instead of `computing delta`.

### Scenario 3: Import the latest LOINC release

LOINC has no delta-generator equivalent; this path is always full-import.

1. Navigates to the LOINC CodeSystem edit page → opens *Import LOINC release*.
2. Picks the file → clicks *Confirm*. Same streamed upload → Minio path as SNOMED.
3. Returns `LorqueProcess` id; UI polls.
4. Background job downloads from Minio to `/tmp`, hands the file path to `LoincService.importLoinc(...)` (reading from disk, not a `byte[]`).
5. Progress updates as the CSV parser advances; success / failure routes through the same Lorque mechanism.

### Scenario 4: Seed a baseline for an already-imported edition

A deployment has been running for months with a SNOMED International edition imported pre-redesign; the archive isn't in Bob yet, so the next normal import would skip the delta step (no baseline available).

1. Admin grabs the original zip from their archive (the file IHTSDO published).
2. Opens *Stored archives* in the SNOMED page and clicks *Upload archive (no import)*.
3. Picks the file and fills in `edition=international`, `effectiveTime=20260101`. UI calls `POST /bob/objects?container=snomed` with those values + the file as multipart.
4. Endpoint streams the upload → Minio, writes a `bob.object` row with `meta={"terminology":"snomed","edition":"international","effectiveTime":"20260101","filename":"..."}` and `meta.importStatus` unset. **Nothing is pushed to Snowstorm.** The new row appears in the *Stored archives* table.
5. The next normal SNOMED import (Scenario 1) finds the uploaded row as its baseline and computes a delta against it.

LOINC seeding works the same way through `POST /bob/objects?container=loinc` and the LOINC *Stored archives* page.

### Scenario 5: Browse and manage stored archives

An operator wants to audit what's currently in Bob, free up an old archive, or re-import from a stored archive without re-uploading the file.

1. Opens *Stored archives* in the SNOMED page → table lists every `bob.object` row with `container="snomed"`. Columns: *Edition*, *Effective time*, *Filename*, *Import status*, *Size*, *Stored at*.
2. Filters by status (unset / `RUNNING` / `COMPLETED` / `FAILED`) and by edition.
3. Per row:
    - *Trigger import* — calls `POST /snomed/imports?fromObjectId={uuid}` to start a Lorque job against the stored Minio object, no new upload required. Useful for retrying a failed import after fixing Snowstorm.
    - *Delete* — calls `DELETE /bob/objects/{uuid}`. Soft-deletes the row and removes the Minio object. Prompts for confirmation if the row is the most-recent baseline for an edition.

The LOINC *Stored archives* page works identically. Wiki archives are not surfaced here — they have their own page-attachment UI.

### Scenario 6: Snowstorm crashes mid-import

A long-running SNOMED import is in progress when Snowstorm becomes unreachable.

1. The Lorque job's Snowstorm push fails. `lorqueProcessService.fail(processId, ProcessResult.text("Snowstorm error: <body>"))` is called.
2. UI polling sees `status=FAILED` and shows the recorded error text.
3. Operator restarts Snowstorm. They re-trigger the import; because the archive is already in Minio, the system could (future enhancement) offer a "retry without re-uploading" — for now, the operator re-runs the same flow and the archive is overwritten in Minio (or skipped if file hash matches).
4. TermX JVM heap is unaffected — the failure stays in the background thread.

## API surface (new)

### Generic Bob storage endpoints

These are new endpoints on `BobObjectController`, used by every terminology and by the Wiki module's read paths.

| Method + path | Purpose | Returns |
|---|---|---|
| `POST /bob/objects?container=snomed\|loinc\|wiki` | Streamed multipart upload. Form fields: `description`, `meta` (JSON), file part. Stores in `bob.object` + Minio. Authz keyed off `container`. | `BobObject` |
| `GET /bob/objects?container=…&meta.key=value&…` | List, filterable on container + JSONB meta keys. Reuses existing `BobObjectQueryParams`. | `BobObject[]` |
| `GET /bob/objects/{uuid}` | Single object metadata. | `BobObject` |
| `GET /bob/objects/{uuid}/content` | Streamed download (`StreamedFile`). | binary |
| `PATCH /bob/objects/{uuid}` | Update `meta` / `description`. Used by the import job to write back `importStatus` and `lorqueProcessId`. | `BobObject` |
| `DELETE /bob/objects/{uuid}` | Soft-delete the row + remove the Minio object. | 204 No Content |

### Terminology import endpoints

| Method + path | Purpose | Returns |
|---|---|---|
| `POST /snomed/imports` *(replacing existing)* | Trigger SNOMED import. EITHER `?fromObjectId={uuid}` (use a previously-stored archive) OR multipart file (one-step: stores via `BobObjectService` then triggers). | `LorqueProcess` (HTTP 202) |
| `POST /loinc/imports` *(replacing existing)* | Same shape for LOINC. | `LorqueProcess` |
| `GET /lorque-processes/{id}/status` *(existing)* | Poll job status. | `{status, progress, note}` |
| `GET /lorque-processes/{id}` *(existing)* | Full job record incl. `result_text` on failure. | `LorqueProcess` |

The synchronous upload paths that buffer everything in heap are **removed**, not deprecated. There is no fallback to the old behaviour.

There is no separate `/snomed/imports/uploads` endpoint. Upload-only goes through `POST /bob/objects?container=snomed`. The per-container authorizer applies the same `snomed-ct.CodeSystem.write` privilege check that a wrapper would have, so a wrapper would have been pure indirection.

## UI surface (new)

A new *Stored archives* component lives on both the SNOMED and LOINC CodeSystem pages. It renders `GET /bob/objects?container={terminology}` as a filterable, paginated table:

- **Columns:** Edition · Effective time · Filename · Import status · Size · Stored at · Actions
- **Filters:** Edition (free text), Effective time (range), Import status (*unset* / `RUNNING` / `COMPLETED` / `FAILED`)
- **Per-row actions:**
  - *Trigger import* — visible when `meta.importStatus` is unset or `FAILED`. Calls `POST /<terminology>/imports?fromObjectId={uuid}` to start an import job against the already-stored Minio object — no new upload.
  - *View job* — visible when the row has a `meta.lorqueProcessId`. Routes to the job-status modal already used by the dry-run scan.
  - *Delete* — calls `DELETE /bob/objects/{uuid}`. Confirmation prompt; warns if the row is the most-recent successful baseline for an edition (deleting it means the next SNOMED import skips the delta step).
- **Top-of-page actions:**
  - *Upload archive (no import)* — opens a small modal: file picker + `edition` + `effectiveTime`. Calls `POST /bob/objects?container={terminology}` with those values as `meta` keys.
  - *Import new release* — opens the existing import modal (the streamed-upload flow described in Scenarios 1–3).

The Wiki page-attachment UI is **not** changed. It keeps calling `/pages/{id}/files` because that's the right convenience layer for page-bound attachments. The new generic `/bob/objects?container=wiki` endpoints exist for future cross-cutting tooling (e.g. an admin "all Bob objects" view) but aren't wired into the existing Wiki UI.

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
