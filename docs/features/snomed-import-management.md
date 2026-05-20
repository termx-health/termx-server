# SNOMED CT RF2 Import and Management

## Description

This feature lets terminology managers upload, scan, diff, and import SNOMED CT RF2 release archives (full International edition, national extensions, deltas) from the TermX UI without the heap-OOM risk of buffering ~549 MB zips in memory. Heavy work runs as background Lorque jobs and reports progress; archives live in Minio (via the `bob` storage layer) so retries, deltas, and cross-edition comparisons reuse the same upload.

It pairs three flows:

1. **Dry-run scan** — uploads or selects an RF2 zip, parses it, and produces a structured report of new / modified / invalidated concepts (with designations and optionally relationships+attributes) plus aggregate stats. The same upload can be pushed to Snowstorm in one click without re-uploading the file.
2. **Delta calculation** — picks a baseline archive on the same `branchPath` and runs the IHTSDO `delta-generator-tool` against the current archive; the produced delta zip lands back in Bob (tagged `meta.kind = "delta"`) and can itself be imported or scanned later.
3. **Snowstorm push** — submits a stored or just-uploaded archive to Snowstorm, streaming bytes from Minio rather than re-uploading from the browser.

A complementary **concept-usage lookup** answers "which TermX-managed CodeSystem supplements, ValueSet rules, and stored expansions still reference these SNOMED codes?" — useful when triaging the inactivated-concept list from a scan.

**Key capabilities**

- **Archive storage** in Bob (`bob.object` / `bob.object_storage`) under container `snomed`, tagged with `meta.branchPath`, `meta.shortName`, `meta.effectiveTime`, and `meta.kind` ("delta" for tool output).
- **Streamed multipart upload** — bytes go straight from network → temp file → Minio; nothing buffered in heap.
- **Per-archive scan** via `POST /snomed/imports/scan/from-archive`, async via Lorque, two modes (`summary` — concepts + descriptions + text definitions only, default; `full` — also relationships + language-refset acceptability + attributes). `summary` is roughly 3–4× faster than `full` on the International edition.
- **Streamed Snowstorm push** via `POST /snomed/imports/from-archive` — `snowstormClient.uploadRF2File(jobId, () -> bobObjectService.loadContentStream(archive))` re-streams from Bob, never re-buffers.
- **Delta-against-baseline** via `POST /snomed/archives/{uuid}/delta` — spools both archives Bob → temp files, invokes the vendored `DeltaGeneratorTool-3.0.0.jar` subprocess, uploads the produced delta zip back into Bob.
- **Archive detail page** with: per-file row stats (`GET /snomed/archives/{uuid}/file-stats`), diff candidates (`GET /snomed/archives/{uuid}/diff-candidates` — same `branchPath`, excludes delta-kind archives), Calculate Delta action, and the latest scan result link.
- **Concept-usage lookup** — accepts plain codes, a JSON array, or a full `SnomedRF2ScanResult` JSON (auto-extracts invalidated codes); returns every CodeSystem supplement / ValueSet rule / stored expansion that still references those codes, with links back to the affected resource edit pages.
- **Cache + import tracking** — `sys.snomed_rf2_upload` caches scan/import zips (bytea or Bob UUID reference) with 7-day retention; `sys.snomed_import_tracking` records the Snowstorm jobId, branchPath, type, and status of every push.
- **Standalone Python equivalents** under `termx-server/scripts/` for offline / batch / CI use of the scan and concept-usage logic.

## Configuration

### Properties

| Property | Env variable | Default | Description |
|---|---|---|---|
| `bob.minio.url` / `bob.minio.access-key` / `bob.minio.secret-key` | `BOB_MINIO_*` | (deployment) | Minio endpoint + credentials — shared with all Bob containers. |
| `snowstorm.url` | — | `https://snowstorm.termx.org/` | Snowstorm base URL — consulted on import push. |
| `snowstorm.user` / `snowstorm.password` | — | (deployment) | Basic Auth. Empty values send no `Authorization` header. |
| `micronaut.server.max-request-size` | — | `629145600` (600 MB) | Multipart upload cap — SNOMED International is ~549 MB. |
| `micronaut.server.multipart.max-file-size` | — | `629145600` (600 MB) | Same. |
| `termx.snomed.delta-generator.timeout-seconds` | — | `1800` (30 min) | Hard cap on the `DeltaGeneratorTool` subprocess. |

### Cache retention

`sys.snomed_rf2_upload` rows older than **7 days** are soft-deleted and their `data` column nulled by a scheduled cleanup task (`SnomedRF2UploadCacheService.scheduledCleanup`, every 6 h, 10-min initial delay). Retention is hard-coded in `RETENTION_DAYS`.

### Privileges

| Privilege | Used for |
|---|---|
| `snomed-ct.CodeSystem.read` | Open the SNOMED section, list archives, trigger dry-run scan, view scan results, run concept-usage lookup. |
| `snomed-ct.CodeSystem.write` | Upload an archive, delete one, trigger a Snowstorm import, kick off delta calculation. |

## Use-Cases

### Scenario 1: Pre-flight a quarterly International release

A terminology manager downloads `SnomedCT_InternationalRF2_PRODUCTION_20260101T120000Z.zip` and uploads it via the SNOMED CodeSystem edit page's *Stored archives* card. The zip lands in Bob under container `snomed` with `meta.branchPath = "MAIN"`. They open the archive detail page and click *Scan (summary)* — a Lorque job runs in the background, the page polls its status, and on completion the scan-result page renders three tables (NEW, MODIFIED, INVALIDATED) plus aggregate stats. Clicking *Proceed with import* pushes the cached zip to Snowstorm without a second upload.

### Scenario 2: Compute a delta against the previous edition

From the archive detail page of the newly uploaded zip the manager opens the *Diff candidates* section — it lists every prior archive on the same `branchPath` whose `meta.kind` is not "delta". They pick last quarter's release and click *Calculate Delta*. The server spools both archives from Bob to temp files, invokes the `DeltaGeneratorTool-3.0.0.jar` subprocess, captures the `Rows exported: N` stdout line, and uploads the produced delta zip back to Bob with `meta.kind = "delta"`. The archive list refreshes to show the new delta archive; it can itself be scanned or pushed to Snowstorm.

### Scenario 3: Audit existing TermX resources for inactivated codes

After scanning a new release the manager copies the JSON of `SnomedRF2ScanResult` (or just the `invalidatedConcepts[].code` array) and pastes it into the *Concept usage* page. The page extracts the codes, posts to `POST /snomed/concept-usage`, and renders a table of every CodeSystem supplement, ValueSet rule, and stored expansion that still references one of those codes, with links straight to the affected edit page. The manager can then fix each one before pushing the release to Snowstorm.

### Scenario 4: Bulk-store national extensions for offline reference

The manager uploads several national-extension zips with different `meta.shortName` (e.g. `SNOMED-US`, `SNOMED-UK-CLINICAL`). The Bob list endpoint with a JSONB containment filter (`meta=...`) keeps the *Stored archives* card scoped per code-system, so each CodeSystem edit page only shows its own uploads.

## Implementation Highlights

### Backend

| File | Purpose |
|---|---|
| `snomed/.../integration/SnomedController.java` | All `/snomed/*` endpoints: `imports`, `imports/scan`, `imports/scan/{cacheId}/proceed`, `imports/scan/result/{lorqueProcessId}`, `imports/from-archive`, `imports/scan/from-archive`, `archives/{uuid}/file-stats`, `archives/{uuid}/diff-candidates`, `archives/{uuid}/delta`, `archives/{uuid}/latest-scan-result`, `concept-usage`. |
| `snomed/.../integration/SnomedService.java` | `importRF2File(byte[])` (legacy heap-buffer path) and `importRF2FileFromBob(BobObject)` (streamed). Both record into `sys.snomed_import_tracking`. |
| `snomed/.../integration/SnomedRF2ImportFromArchiveService.java` | Streams a Bob archive to Snowstorm via `snowstormClient.uploadRF2File(jobId, () -> bobObjectService.loadContentStream(archive))`. |
| `snomed/.../integration/rf2/scan/SnomedRF2ScanService.java` | Parses the RF2 release rows in either `summary` or `full` mode; produces `SnomedRF2ScanResult` with `newConcepts[]`, `modifiedConcepts[]`, `invalidatedConcepts[]`, and `stats`. Wrapped in `SnomedRF2ScanEnvelope` (JSON + Markdown) for download. |
| `snomed/.../integration/SnomedDeltaGeneratorRuntime.java` | Extracts the vendored `DeltaGeneratorTool-3.0.0.jar` to a stable temp path on first use; invokes as a subprocess (`java -Xms1G -Xmx4G -jar … <old.zip> <new.zip> [--latest-state]`); captures the `Rows exported: NNN` line for the progress report. |
| `snomed/.../integration/SnomedDeltaCalculateService.java` | Orchestrates delta calculation: spools baseline + current Bob → temp files → runtime → uploads delta back to Bob with `meta.kind = "delta"`. Returns a `LorqueProcess`; result JSON includes `{deltaUuid, rowsExported, durationMs, latestState}`. |
| `snomed/.../integration/SnomedBobContainerAuthorizer.java` | Per-container Bob authz: container `snomed` maps to `snomed-ct.CodeSystem.*` privileges. |
| `snomed/src/main/resources/snomed/delta-generator-tool/DeltaGeneratorTool-3.0.0.jar` | Vendored IHTSDO delta-generator-tool. |
| `termx-core/.../resources/sys/changelog/sys/70-snomed_import_tracking.sql` | `sys.snomed_import_tracking` schema. |
| `termx-core/.../resources/sys/changelog/sys/71-snomed_rf2_upload.sql` | `sys.snomed_rf2_upload` cache schema. |

### Frontend

| File | Purpose |
|---|---|
| `app/src/app/integration/snomed/containers/management/codesystem/snomed-codesystem-edit.component.html` | Embeds `<tw-bob-archives container="snomed" [meta]="{shortName, branchPath}" ...>` for per-code-system archive management. |
| `app/src/app/integration/snomed/containers/management/codesystem/snomed-archive-detail.component.ts` | Archive detail page — filename / branchPath / upload metadata; per-file row stats; diff-candidate picker; *Calculate Delta* and *Import / Scan* actions. |
| `app/src/app/integration/snomed/containers/management/codesystem/snomed-rf2-scan-result.component.ts` | Scan-result page — NEW / MODIFIED / INVALIDATED tables, aggregate stats, *Check usage* link, *Proceed with import* button. |
| `app/src/app/integration/snomed/containers/usage/snomed-concept-usage.component.ts` | Concept-usage lookup — accepts codes / JSON array / full scan result; renders affected resources with edit-page links. |
| `app/src/app/sys/_lib/bob/components/bob-archives.component.ts` | Reusable "Stored archives" card; same component as LOINC import — container-scoped via the `container` and `meta` inputs. |

## Operational Notes

- **Heap-safe end-to-end** — neither the dry-run scan, the delta tool, nor the Snowstorm push ever buffers the full RF2 archive in JVM heap. The legacy `POST /snomed/imports` path (which does buffer) is kept only for backwards-compatible callers.
- **Snowstorm import status** — `sys.snomed_import_tracking` records the Snowstorm jobId returned by `createImportJob`; admins can poll Snowstorm directly with that jobId for completion / errors. There is no automatic poller yet.
- **Delta tool licensing** — the IHTSDO `delta-generator-tool` is vendored as a jar inside the repo; updates require swapping the file and bumping the path constant in `SnomedDeltaGeneratorRuntime`.
- **Concept-usage scope** — the lookup walks CodeSystem supplements, ValueSet rules, and stored expansions only. It does not currently flag SNOMED references inside MapSet associations or external StructureDefinitions.
- **No automatic re-scan on archive replace** — uploading a new zip with the same effective time creates a new Bob row; admins must trigger scan and delta calculation explicitly.
- **National extensions** require their own `branchPath` on upload so the diff-candidate picker correctly groups them; uploading a Belgian extension with `branchPath = "MAIN"` would incorrectly let it diff against the International edition.
