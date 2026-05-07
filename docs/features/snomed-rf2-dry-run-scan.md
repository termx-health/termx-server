# SNOMED RF2 Dry-Run Scan and Concept-Usage Utility

## Description

This feature lets terminology managers triage a SNOMED RF2 release **before** it is imported into Snowstorm, and find which TermX-managed artefacts will be affected by concepts that the release inactivates.

It packages two related utilities:

1. **RF2 dry-run scan** — uploads an RF2 zip, parses the changed rows, and produces a report of what would change (new / modified / invalidated concepts with their designations and attributes) plus aggregate stats. The same upload can then be pushed into Snowstorm in one click without re-uploading the file.
2. **SNOMED concept-usage lookup** — given a list of SNOMED concept codes (typed in, pasted, or extracted automatically from a dry-run report), returns every TermX CodeSystem supplement, ValueSet rule, and stored ValueSet expansion that still references those codes.

**Key capabilities**

- Per-release change report (NEW / MODIFIED / INVALIDATED) with stats, designations, and (optionally) attributes
- Mode parameter: `summary` (default — concepts + descriptions + text-definitions only; ~3-4× faster on a full International edition zip) and `full` (also relationships and language-refset → acceptability and attributes)
- Auto-download of both JSON and Markdown report formats on result open
- *Proceed with import* action that re-uses the cached zip server-side — no second upload of a 549 MB file
- Three-phase progress indicator in the import modal: *Uploading file…* (real %) → *Scanning RF2 file… — parsing descriptions* (real % from the server) → *Importing to Snowstorm…* (indeterminate)
- Concept-usage page accepts plain codes, a JSON array, or a full `SnomedRF2ScanResult` JSON (auto-extracts invalidated codes); results table links back to the affected CodeSystem / ValueSet edit pages
- Standalone Python equivalents under [`termx-server/scripts/`](../../scripts/README.md) for offline / batch / CI use

## Configuration

### Properties

The dry-run scan itself has no dedicated configuration. It piggy-backs on existing knobs:

| Property | Env variable | Default | Description |
|----------|--------------|---------|-------------|
| `micronaut.server.max-request-size` | — | `629145600` (600 MB) | Hard cap on multipart upload size; SNOMED International is ~549 MB |
| `micronaut.server.multipart.max-file-size` | — | `629145600` (600 MB) | Same |
| `snowstorm.url` | — | `https://snowstorm.termx.org/` | Snowstorm base URL — only consulted on *Proceed with import* |
| `snowstorm.user` / `snowstorm.password` | — | (set per deployment) | Basic Auth for Snowstorm. Empty/blank value sends no `Authorization` header (PR #104) |

### Cache retention

Uploaded zips are cached in `sys.snomed_rf2_upload`. A scheduled task (`SnomedRF2UploadCacheService.scheduledCleanup`, runs every 6 h, initial delay 10 min) soft-deletes rows older than **7 days** and clears the bytea so the disk space is released. Retention is hard-coded in `RETENTION_DAYS`.

### Privileges

| Privilege | Used for |
|-----------|----------|
| `snomed-ct.CodeSystem.view` | Trigger dry-run scan, view scan-result page, run concept-usage lookup |
| `snomed-ct.CodeSystem.edit` | *Proceed with import* on the scan-result page (the scan itself only needs `view`) |

## Use-Cases

### Scenario 1: Pre-flight a quarterly International release

A terminology manager has just downloaded `SnomedCT_InternationalRF2_PRODUCTION_20260101T120000Z.zip` and wants to know what changed since the previous release before pushing it to Snowstorm.

1. Navigates to the SNOMED CodeSystem edit page → opens the *Import from RF2* modal
2. Picks the file, ticks **Dry run**, leaves *Full analysis* unticked
3. Clicks *Confirm* → bar shows *Uploading file… (n%)* during upload, *Scanning RF2 file… — parsing descriptions* (etc.) during scan
4. Routes to the scan-result page — JSON and Markdown reports auto-download, three tables show NEW (3,525) / MODIFIED (1,133) / INVALIDATED (576) concepts
5. Reviews the invalidated list, decides the release is fine to import
6. Clicks *Proceed with import* → cached zip is sent to Snowstorm without re-upload; modal switches to *Importing to Snowstorm…* until the Snowstorm job completes

### Scenario 2: Find which local artefacts will break

After the scan in Scenario 1, the manager wants to know which CodeSystem supplements / ValueSets reference the 576 invalidated concepts.

1. On the scan-result page clicks *Check usage*
2. Routes to the concept-usage page with the 576 codes pre-filled (extracted from `invalidatedConcepts[].conceptId`)
3. Clicks *Search* → results table lists each hit by *Resource type* (CodeSystemSupplement / ValueSet / ValueSetExpansion), *Resource id*, *Version*, *Concept code*, *Location*
4. Each *Resource id* links to the affected resource's edit page so the issue can be triaged or fixed before the import lands

### Scenario 3: Standalone offline scan

A reviewer wants the same change report without spinning up the full server stack.

```sh
python3 termx-server/scripts/snomed_rf2_scan.py \
    ~/Downloads/SnomedCT_InternationalRF2_PRODUCTION_20260101T120000Z.zip \
    --cutoff 20251001 \
    -o /tmp/scan-since-20251001.json
```

The script mirrors the in-app diff engine line-for-line and produces a `SnomedRF2ScanResult` JSON the same UI / tooling can consume. Pair with `snomed_concept_usage.py` (psycopg2-based) for usage lookup against the live database.

### Scenario 4: Quick re-scan with a different cutoff

The default cutoff is the latest release date present in the zip. To filter changes since a different date (e.g. include both 20251001 and 20260101 effectiveTimes), pass `--cutoff 20251001` to the CLI or re-upload with the same flag — the algorithm only includes rows whose `effectiveTime >= cutoff`.

## API

All endpoints are under `/snomed`.

| Method | Path | Privilege | Description |
|--------|------|-----------|-------------|
| `POST` | `/snomed/imports/scan` | `SNOMED_VIEW` | multipart upload (`file` + `request` JSON), kicks off async dry-run scan, returns a `LorqueProcess` |
| `GET`  | `/snomed/imports/scan/result/{lorqueProcessId}` | `SNOMED_VIEW` | parsed `SnomedRF2ScanEnvelope` (`{json, markdown}`) once the scan completes |
| `POST` | `/snomed/imports/scan/{cacheId}/proceed` | `SNOMED_EDIT` | submits the cached zip to Snowstorm without re-upload; returns `{jobId}` |
| `POST` | `/snomed/concept-usage` | `SNOMED_VIEW` | body `{codes: List<String>}` → returns `List<SnomedConceptUsage>` |
| `POST` | `/snomed/imports` | `SNOMED_EDIT` | (existing) direct Snowstorm import — bypasses the scan |

### `SnomedImportRequest` (request part of `/imports/scan`)

```json
{
  "branchPath": "MAIN",
  "type": "DELTA",
  "createCodeSystemVersion": false,
  "dryRun": true,
  "mode": "summary"
}
```

| Field | Type | Notes |
|-------|------|-------|
| `branchPath` | string | Snowstorm branch — passed through to *Proceed with import* |
| `type` | string | `DELTA` / `SNAPSHOT` / `FULL` (label only — algorithm is identical for all three) |
| `createCodeSystemVersion` | boolean | Forwarded to Snowstorm during *Proceed* |
| `dryRun` | boolean | Has no effect on `/imports/scan` (always a scan) — keeps the legacy DTO compatible |
| `mode` | string | `summary` (default) or `full`. Ignored for the legacy `/imports` endpoint |

### `SnomedRF2ScanResult`

```json
{
  "branchPath": "MAIN",
  "rf2Type": "SNAPSHOT",
  "releaseEffectiveTime": "20260101",
  "scannedAt": "2026-04-30T15:47:54Z",
  "uploadCacheId": 42,
  "stats": {
    "conceptsAdded": 3525,
    "conceptsModified": 1133,
    "conceptsInvalidated": 576,
    "descriptionsAdded": 13355,
    "descriptionsInvalidated": 1253,
    "relationshipsAdded": 0,
    "relationshipsInvalidated": 0
  },
  "newConcepts":         [{"conceptId": "...", "effectiveTime": "...", "moduleId": "...", "definitionStatusId": "...", "designations": [...], "attributes": [...]}],
  "modifiedConcepts":    [{"conceptId": "...", "addedDesignations": [...], "removedDesignations": [...], "addedAttributes": [...], "removedAttributes": [...]}],
  "invalidatedConcepts": [{"conceptId": "...", "effectiveTime": "...", "moduleId": "...", "designations": [...]}]
}
```

In `summary` mode, `relationshipsAdded`/`relationshipsInvalidated` are `0`, designations carry `acceptability="none"`, and the *MODIFIED* list does not include concepts whose only change in this release was a relationship.

### `SnomedConceptUsage`

```json
[
  {"resourceType": "CodeSystemSupplement", "resourceId": "snomed-est-supplement", "resourceVersion": null,    "conceptCode": "264936004", "location": "concept"},
  {"resourceType": "ValueSet",             "resourceId": "my-procedures-vs",      "resourceVersion": "1.0.0", "conceptCode": "7377003",   "location": "rule"},
  {"resourceType": "ValueSetExpansion",    "resourceId": "vs-with-expansion",     "resourceVersion": "1.2",   "conceptCode": "264936004", "location": "expansion"}
]
```

## Testing

### Quick start (in-app, dry-run)

```sh
# 1. open https://<termx>/integration/snomed/codesystems/SNOMEDCT/edit → Import from RF2
# 2. pick a small DELTA zip, tick "Dry run", click Confirm
# 3. verify routing to /integration/snomed/codesystems/SNOMEDCT/rf2-scan-result
# 4. confirm JSON + Markdown auto-downloaded, three tables populated
```

### Quick start (CLI)

```sh
# Dry-run summary scan
python3 termx-server/scripts/snomed_rf2_scan.py \
    ~/Downloads/SnomedCT_InternationalRF2_PRODUCTION_20260101T120000Z.zip \
    --cutoff 20251001 -o /tmp/scan.json

# Concept-usage lookup against the dev database
python3 termx-server/scripts/snomed_concept_usage.py \
    --codes-file /tmp/scan.json \
    --dsn 'postgres://termx:termx@localhost:5432/termx' \
    -o /tmp/usage.json
```

### Test scenarios

| # | Scenario | Expected |
|---|----------|----------|
| 1 | DELTA zip with 1 new + 1 modified + 1 invalidated concept | Stats: `conceptsAdded=1, conceptsModified=1, conceptsInvalidated=1`. Snowstorm `/imports` not called. |
| 2 | International SNAPSHOT, `mode=summary` | Relationship and Language-refset files skipped; ~3-4× faster than `mode=full`. |
| 3 | International SNAPSHOT, `mode=full` | Includes attributes on new concepts and `acceptability` on designations. |
| 4 | *Proceed with import* on a successful scan | New `sys.snomed_import_tracking` row is created; `sys.snomed_rf2_upload.imported = true`. |
| 5 | Concept-usage with the JSON output of (1) pasted in | Auto-extracts invalidated codes; results return matches against seeded supplement/VS rule/VS expansion fixtures. |
| 6 | Snowstorm rejects *Proceed* with HTTP 403 (auth) | Server logs **one** `WARN  Snowstorm import call failed: HTTP 403 …` line (no stack); frontend shows `SN201` notification; modal closes with file selection cleared. |
| 7 | Cleanup scheduler | After 7 days (or when triggered manually), `sys.snomed_rf2_upload.zip_data` is `''::bytea` and `sys_status='C'` for old rows. |

## Data Model

### `sys.snomed_rf2_upload` (new)

Liquibase changeset: `termx:snomed_rf2_upload` in [`termx-core/src/main/resources/sys/changelog/sys/71-snomed_rf2_upload.sql`](../../termx-core/src/main/resources/sys/changelog/sys/71-snomed_rf2_upload.sql).

| Column | Type | Description |
|--------|------|-------------|
| `id` | `bigserial` | PK |
| `branch_path` | `text not null` | Snowstorm branch from the upload request |
| `rf2_type` | `text not null` | `DELTA` / `SNAPSHOT` / `FULL` |
| `create_code_system_version` | `boolean default false` | Forwarded to Snowstorm during *Proceed* |
| `filename` | `text` | Original upload filename (informational) |
| `zip_size` | `bigint` | Bytes |
| `zip_data` | `bytea not null` | Raw RF2 zip; cleared (`''::bytea`) on soft-delete by the cleanup task |
| `scan_lorque_id` | `bigint` | Link to `sys.lorque_process.id` of the scan job |
| `imported` | `boolean not null default false` | `true` after *Proceed* succeeded |
| `started` | `timestamptz not null default current_timestamp` | Upload time |
| `imported_at` | `timestamptz` | When *Proceed* succeeded |
| `sys_*` | (6 cols) | Standard sys-columns; soft-delete via `sys_status='C'` |

Indexes: `snomed_rf2_upload_lorque_idx (scan_lorque_id)`, `snomed_rf2_upload_started_idx (started)`.

### `sys.lorque_process` (extended)

Two columns added by `termx:lorque_process-progress` in [`20-lorque_process.sql`](../../termx-core/src/main/resources/sys/changelog/sys/20-lorque_process.sql):

| Column | Type | Description |
|--------|------|-------------|
| `progress_percent` | `int` | 0-100; populated by `LorqueProcessService.reportProgress(...)` |
| `progress_note` | `text` | Free-form phase label, e.g. *parsing descriptions*, *computing diff* |

Backwards compatible — existing call sites that don't write progress see `NULL`.

### Result envelope

Lorque result for a `snomed-rf2-scan` process is a UTF-8 JSON of `SnomedRF2ScanEnvelope`:

```json
{
  "json":     { /* SnomedRF2ScanResult */ },
  "markdown": "# SNOMED RF2 dry-run scan\n\n…"
}
```

The frontend `loadScanResult(processId)` endpoint deserialises and returns this envelope so the UI doesn't have to base64-decode the bytea result.

## Architecture

```mermaid
sequenceDiagram
    participant U as User
    participant W as termx-web
    participant S as termx-server
    participant DB as Postgres (sys)
    participant SS as Snowstorm

    Note over U,SS: Dry-run scan flow
    U->>W: pick zip + tick "Dry run" + Confirm
    W->>S: POST /snomed/imports/scan (multipart)
    S->>DB: insert into snomed_rf2_upload (zip_data, ...)
    S->>DB: insert into lorque_process (status=running)
    S-->>W: LorqueProcess { id }
    activate S
    Note right of S: async runnable
    S->>S: SnomedRF2ZipParser.parse() with phaseReporter
    loop every parsed file kind
        S->>DB: update lorque_process set progress_percent=N, progress_note='parsing X'
    end
    S->>S: SnomedRF2DiffEngine.classify()
    S->>S: render JSON + Markdown envelope
    S->>DB: update lorque_process set status=completed, result=...
    deactivate S

    loop poll every 1.5s
        W->>S: GET /lorque-processes/{id}
        S-->>W: { progressPercent, progressNote, status }
    end
    W->>S: GET /snomed/imports/scan/result/{id}
    S-->>W: SnomedRF2ScanEnvelope
    W->>U: route to scan-result page; auto-download JSON + MD

    Note over U,SS: Optional: Proceed with import
    U->>W: click "Proceed with import"
    W->>S: POST /snomed/imports/scan/{cacheId}/proceed
    S->>DB: load zip_data from snomed_rf2_upload
    S->>SS: POST /imports → jobId
    S->>SS: POST /imports/{jobId}/archive (multipart, bytea)
    S->>DB: update snomed_rf2_upload set imported=true
    S->>DB: insert into snomed_import_tracking
    S-->>W: { jobId }
```

```mermaid
flowchart LR
    subgraph termx-server
        SC[SnomedController]
        SS[SnomedService.importRF2File]
        RS[SnomedRF2ScanService]
        RP[SnomedRF2ZipParser]
        DE[SnomedRF2DiffEngine]
        UC[SnomedRF2UploadCacheService]
        UR[SnomedRF2UploadCacheRepository]
        CU[SnomedConceptUsageService]
        CR[SnomedConceptUsageRepository]
        LP[LorqueProcessService]
        SCli[SnowstormClient]
    end

    SC -->|/imports/scan| RS
    SC -->|/imports/scan/result| LP
    SC -->|/imports/scan/{id}/proceed| SS
    SC -->|/concept-usage| CU
    SC -->|/imports| SS
    RS --> RP
    RS --> DE
    RS --> UC
    RS --> LP
    UC --> UR
    SS --> SCli
    SS -->|on HttpClientError| ApiError(SN201)
    CU --> CR
```

## Technical Implementation

### Source files (server)

| File | Description |
|------|-------------|
| [`termx-api/.../snomed/rf2/SnomedImportRequest.java`](../../termx-api/src/main/java/org/termx/snomed/rf2/SnomedImportRequest.java) | DTO; `dryRun` and `mode` fields |
| [`termx-api/.../snomed/rf2/SnomedRF2Upload.java`](../../termx-api/src/main/java/org/termx/snomed/rf2/SnomedRF2Upload.java) | Cache-row entity |
| [`termx-api/.../snomed/rf2/scan/`](../../termx-api/src/main/java/org/termx/snomed/rf2/scan/) | `SnomedRF2ScanResult` + `SnomedRF2ScanEnvelope` + designation/attribute/concept DTOs |
| [`termx-api/.../snomed/concept/SnomedConceptUsage.java`](../../termx-api/src/main/java/org/termx/snomed/concept/SnomedConceptUsage.java) | Usage-row DTO |
| [`snomed/.../integration/SnomedController.java`](../../snomed/src/main/java/org/termx/snomed/integration/SnomedController.java) | Endpoints `/imports/scan*` and `/concept-usage` |
| [`snomed/.../integration/SnomedService.java`](../../snomed/src/main/java/org/termx/snomed/integration/SnomedService.java) | `importRF2File` (with the `HttpClientError → SN201` catch) |
| [`snomed/.../integration/rf2/scan/SnomedRF2ZipParser.java`](../../snomed/src/main/java/org/termx/snomed/integration/rf2/scan/SnomedRF2ZipParser.java) | Streams the zip; `parse(bytes, phaseReporter, fullMode)` |
| [`snomed/.../integration/rf2/scan/SnomedRF2DiffEngine.java`](../../snomed/src/main/java/org/termx/snomed/integration/rf2/scan/SnomedRF2DiffEngine.java) | NEW / MODIFIED / INVALIDATED classification by `effectiveTime` cutoff |
| [`snomed/.../integration/rf2/scan/SnomedRF2ScanService.java`](../../snomed/src/main/java/org/termx/snomed/integration/rf2/scan/SnomedRF2ScanService.java) | Orchestrator: cache + lorque + async + progress reporting + markdown rendering |
| [`snomed/.../integration/rf2/scan/SnomedRF2UploadCacheService.java`](../../snomed/src/main/java/org/termx/snomed/integration/rf2/scan/SnomedRF2UploadCacheService.java) | 7-day soft-delete cleanup `@Scheduled fixedDelay="6h"` |
| [`snomed/.../integration/rf2/scan/SnomedRF2UploadCacheRepository.java`](../../snomed/src/main/java/org/termx/snomed/integration/rf2/scan/SnomedRF2UploadCacheRepository.java) | Direct UPDATEs for `setScanLorqueId` / `markImported`; soft-delete cleanup |
| [`snomed/.../integration/usage/SnomedConceptUsageService.java`](../../snomed/src/main/java/org/termx/snomed/integration/usage/SnomedConceptUsageService.java) | Normalises codes; calls all three repo queries |
| [`snomed/.../integration/usage/SnomedConceptUsageRepository.java`](../../snomed/src/main/java/org/termx/snomed/integration/usage/SnomedConceptUsageRepository.java) | Three SQL queries (supplements / VS rules / VS expansions) |
| [`termx-core/.../core/sys/lorque/LorqueProcessRepository.java`](../../termx-core/src/main/java/org/termx/core/sys/lorque/LorqueProcessRepository.java) | `updateProgress(id, percent, note)` |
| [`termx-core/.../core/sys/lorque/LorqueProcessService.java`](../../termx-core/src/main/java/org/termx/core/sys/lorque/LorqueProcessService.java) | `reportProgress(id, percent, note)` |

### Source files (web)

| File | Description |
|------|-------------|
| `termx-web/app/src/app/integration/snomed/services/snomed-service.ts` | `scanRF2`, `loadScanResult`, `proceedScanImport`, `findConceptUsage` |
| `termx-web/app/src/app/integration/snomed/containers/management/codesystem/snomed-codesystem-edit.component.{ts,html}` | Import modal: *Dry run* + *Full analysis* checkboxes, three-phase progress bar, error toast + auto-close on failure |
| `termx-web/app/src/app/integration/snomed/containers/management/codesystem/snomed-rf2-scan-result.component.{ts,html}` | Scan-result page (stats + 3 tables + auto-download JSON+MD + Proceed) |
| `termx-web/app/src/app/integration/snomed/containers/usage/snomed-concept-usage.component.{ts,html}` | Standalone usage page; flexible textarea (text / JSON array / scan-result JSON) |
| `termx-web/app/src/app/sys/_lib/lorque/services/lorque-lib.service.ts` | `pollProcessProgress(id, destroy$)` — polls `LorqueProcess` every 1.5 s for percent + note |

### Source files (scripts)

| File | Description |
|------|-------------|
| [`scripts/snomed_rf2_scan.py`](../../scripts/snomed_rf2_scan.py) | Standalone CLI mirroring the diff engine; `--mode summary|full`, `--cutoff` |
| [`scripts/snomed_concept_usage.py`](../../scripts/snomed_concept_usage.py) | Standalone CLI mirroring the three usage queries; psycopg2 |
| [`scripts/README.md`](../../scripts/README.md) | Usage docs for both scripts |

### Classification rules

For each RF2 type the scan filters to "change-set rows": rows whose `effectiveTime` equals the cutoff (default = max effectiveTime in the Concept file). For each `conceptId` referenced in the change-set:

| Condition | Classification |
|-----------|----------------|
| Concept row with `active=0` | **INVALIDATED** |
| Concept row with `active=1` | **NEW** |
| No Concept row in change-set, but Description / TextDefinition / Relationship / Language-refset rows | **MODIFIED** |

This is intentionally a no-Snowstorm-call strategy: the entire scan runs from RF2 rows alone, which keeps it fast enough to handle a full International edition zip in seconds.

### Mode trade-offs

| Mode | Files parsed | Extra detail | Speed on Int'l zip |
|------|--------------|--------------|--------------------|
| `summary` (default) | Concept + Description + TextDefinition | – | several × faster |
| `full` | + Relationship + Language-refset | attributes on NEW; acceptability on designations | baseline |

In `summary` mode the *MODIFIED* list does not include concepts whose only change was a relationship — by definition not visible from the parsed files alone.

### Snowstorm error handling

Snowstorm 4xx responses on the *Proceed* path are caught in `SnomedService.importRF2File`:

- One-line `WARN` log: `Snowstorm import call failed: HTTP 403 https://…/snowstorm/snomed-ct/imports`
- Rethrown as `ApiError.SN201.toApiException(Map.of("status", ..., "url", ...))`
- `ApiClientException` carries `httpStatus=400`, so `DefaultExceptionHandler.handleApiException` takes the debug branch — no stack in the server log.
- The frontend extracts `{code, message}` from the kodality `Issue` array and renders the SN201 message in a toast; the import modal closes and clears the file selection.

Genuine async failures that aren't `HttpClientError` (network timeout, NPE in the client lib) propagate untouched.

### Related

- [Email Import Notifications](email-import-notifications.md) — SNOMED imports trigger email summaries when SMTP is configured.
- [SNOMED Server Architecture](architecture.md) — overall server module layout.
- Convention: feature documentation template — [`docs/conventions/feature-documentation.md`](../../../docs/conventions/feature-documentation.md).
