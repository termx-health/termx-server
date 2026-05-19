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

All file storage goes through TermX's existing `bob.object` / `bob.object_storage` tables, which the Wiki module already uses. We add a new container value `"snomed"` / `"loinc"` alongside the existing `"wiki"`, plus new generic REST endpoints on `/bob/objects` so the UI can list everything in storage. Wiki rows are preserved untouched — same table, same Minio bucket; the container value distinguishes them.

```
  UI ──upload──▶ POST /bob/objects?container=snomed
                  │ (streamed multipart)
                  │
                  ▼
              BobObjectService.store
                  │  ① stream multipart → temp file on disk
                  │  ② BobStorage row with container="snomed",
                  │     path="/<edition>/<effective-time>/"
                  │  ③ MinioService.store(object, file)
                  │  ④ BobObject row, meta = {terminology, edition,
                  │       effectiveTime, lorqueProcessId? ...}
                  ▼
                Returns BobObject

  UI ──trigger──▶ POST /snomed/imports?fromObjectId={uuid}
                  │
                  │  ① create LorqueProcess(status=RUNNING)
                  │  ② kick off virtual-thread job, return processId
                  ▼
              SnomedRF2ImportJob (async) ──── BobObjectService.loadContent
                  │                          (streams from Minio to /tmp)
                  │
                  │  look up most-recent COMPLETED BobObject for same edition,
                  │  download baseline to /tmp
                  │
                  │  invoke delta-generator-tool
                  │   (subprocess on /tmp/new + /tmp/baseline → /tmp/delta.zip)
                  │
                  │  SnowstormClient.createImportJob → Location
                  │  SnowstormClient.uploadRF2File(jobId, delta.zip)
                  │
                  │  lorqueProcessService.complete(processId, …)
                  │  patch BobObject meta with importStatus="COMPLETED",
                  │    lorqueProcessId
                  ▼
                Snowstorm

  UI ──list──▶  GET /bob/objects?container=snomed
  UI ──poll──▶  GET /lorque-processes/{id}/status
```

The diagram shows SNOMED. **LOINC follows the same shape with one difference:** there is no equivalent of `delta-generator-tool` for LOINC, so the LOINC job downloads the archive from Bob/Minio and pushes it straight to `LoincService` (which now reads from a `Path` instead of a `byte[]`). Everything else — `/bob/objects` upload, Lorque async, status polling, list UI — is identical and shared.

For convenience the import endpoint also accepts a multipart file directly (`POST /snomed/imports` with a `file` part). That path internally stores the file via `BobObjectService` first, then starts the same Lorque job — so the storage layer is always the single source of truth.

## Key decisions

### D1. Where uploads land — existing Bob storage, new container values

**Decision:** Reuse TermX's existing `BobObjectService` (`bob/src/main/java/org/termx/bob/BobObjectService.java:15–79`). Wiki already uses it with container `"wiki"`; we add `"snomed"` and `"loinc"` for these terminologies. Per-archive metadata (terminology, edition id, effective time, optional lorque process id, import status) goes into the existing `bob.object.meta` JSONB column, which is already GIN-indexed for fast filtering.

**Why:**
- `bob.object` + `bob.object_storage` schema already exists and is in production with Wiki rows in it (`bob/src/main/resources/bob/changelog/bob/01-object.sql`, `02-object_storage.sql`).
- `BobObjectService` already wraps `MinioService` with the transaction boundary, soft-delete semantics, and meta-based querying (`BobObjectQueryParams`).
- No new tables, no new bucket layer, no new service class. Wiki container `"wiki"` is untouched.

**Not introducing:** a new `terminology_import` table, a new `TerminologyArchiveService`. Both were in the previous draft of this doc; both were unnecessary once we realised Bob already does everything we needed.

### D1b. Existing Wiki data is preserved

`bob.object` already holds rows with `container="wiki"` and `meta={"page": pageId, "fileName": …}` written by `WikiAttachmentBobHandler:36–49`. Our changes:
- **Do not touch** Wiki rows. Different `container` value, different `meta` shape — they coexist in the same table without conflict.
- **Do not change** the existing `meta` schema for Wiki rows. SNOMED/LOINC rows use new `meta` keys (`terminology`, `edition`, `effectiveTime`, …) that don't collide with Wiki's keys (`page`, `fileName`).
- **Do not require** a data migration. New columns are not needed because everything fits in the existing JSONB.
- The Wiki module's own endpoints (`/pages/{id}/files`) keep working unchanged. They're domain-specific endpoints layered on top of `BobObjectService`, same as the new SNOMED/LOINC endpoints will be.

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

### D6a. Generic `/bob/objects` REST API for the UI

**Decision:** Add a new generic controller exposing `BobObjectService`:
- `POST /bob/objects` — multipart upload with form fields `container`, `meta` (JSON), `description`. Streams to disk → Minio. Returns the created `BobObject`.
- `GET /bob/objects?container=&meta.key=value&…` — list with filtering. Reuses `BobObjectQueryParams` (existing).
- `GET /bob/objects/{uuid}` — single object metadata.
- `GET /bob/objects/{uuid}/content` — streamed download.
- `PATCH /bob/objects/{uuid}` — update `meta` / `description` only. Used by the import job to write back `importStatus` and `lorqueProcessId` once the import completes.
- `DELETE /bob/objects/{uuid}` — soft-delete + Minio object removal.

**Authz:** per-container. Each container value has registered `BobContainerAuthorizer` beans that map to the relevant module's privileges:

| Container | Read privilege | Write privilege |
| --- | --- | --- |
| `snomed` | `snomed-ct.CodeSystem.read` | `snomed-ct.CodeSystem.write` |
| `loinc` | `loinc.CodeSystem.read` | `loinc.CodeSystem.write` |
| `wiki` | existing Wiki read | existing Wiki write |

The Wiki container is registered through the same plugin so `/bob/objects?container=wiki` works with existing Wiki privileges without behavioural change. If a request hits a container with no registered authorizer it's rejected with 403.

### D6b. SNOMED/LOINC `/imports/uploads` wrappers — **dropped**

The previous draft proposed `POST /snomed/imports/uploads`, `POST /loinc/imports/uploads` as upload-only endpoints. With D6a in place these are redundant: admins use `POST /bob/objects?container=snomed` directly, the per-container authz model gives them the same privilege gate, and the UI uses the same list endpoint as everything else.

A wrapper would only be justified if SNOMED or LOINC needed extra upload-time validation that doesn't apply to other containers (e.g. "verify the zip really is an RF2 archive before storing"). We may revisit this if such validation becomes required; for now, none of it is, and the design ships without `/imports/uploads` endpoints.

### D6c. Retention

- Per-edition: keep the most recent **N** raw uploads in Minio (default `N=3`). Older ones get GC'd by a scheduled job mirroring the existing 6-hour cleanup loop in `SnomedRF2UploadCacheService:53–54`.
- Delta archives (intermediate): delete from `/tmp` after the Snowstorm upload completes — they're regenerable.

## Resolved during review

- **Generalize across terminologies.** The storage + async-job foundation (Phase 1) is shared between SNOMED and LOINC. The delta-generator-tool integration (Phase 3) stays SNOMED-specific because the IHTSDO tool only understands RF2; LOINC has no equivalent delta tool and is handled as a streamed-full-import job.
- **Skip delta-generator when no baseline exists.** First-ever import of an edition (or LOINC version) imports the full snapshot directly. Subsequent imports look up the previous version from Minio and run delta-generator.
- **No fallback to the current synchronous upload.** The existing `/imports` and LOINC `/import` are being replaced, not kept alongside. The current path is the bug; preserving it as a fallback just preserves the bug.
- **delta-generator-tool runs as a subprocess** (D3). Tool is officially SNOMED-supported; subprocess gives clean OOM isolation and avoids dragging its full dependency tree into TermX's classpath.
- **Storage uses the existing Bob backend; no new tables, no new service.** `BobObjectService` is the only storage API; we add new container values (`snomed`, `loinc`) alongside the existing `wiki`. All per-archive metadata sits in the existing JSONB `bob.object.meta` column. Wiki rows are preserved unchanged.
- **Upload + list go through new generic `/bob/objects` REST endpoints**, not per-terminology wrappers. SNOMED- and LOINC-specific upload-only endpoints (`/snomed/imports/uploads`) from the previous draft are dropped — see D6b. The UI list components call `GET /bob/objects?container=snomed` / `?container=loinc`.
- **Per-container authz** is implemented as a plugin point (`BobContainerAuthorizer`), so each module owns its own privilege mapping. Wiki container keeps its existing privileges; SNOMED uses `snomed-ct.CodeSystem.*`; LOINC uses `loinc.CodeSystem.*`.
- **Q6 — Backfill source: admin re-uploads the original archive** through `POST /bob/objects?container=snomed` (or `loinc`). Always available because the admin has the source file; no migration code on the server. The alternative (reading from `SnomedRF2UploadCacheService` DB-blob cache) only covers archives uploaded in the last 7 days, can't be relied on, and is rejected.

## Open questions

| # | Question | Notes |
| --- | --- | --- |
| Q2 | Exact `delta-generator-tool` CLI shape? | Confirm against [github.com/IHTSDO/delta-generator-tool](https://github.com/IHTSDO/delta-generator-tool) README. Best done by whoever picks up the implementation. |
| Q3 | Should `SnomedRF2UploadCacheService` (DB-blob cache for the dry-run scan) move to Bob too? | Probably yes for consistency, but out-of-scope for this redesign. Track separately. |
| Q5 | What happens if delta-generator fails / produces empty delta? | If empty → no-op, mark Lorque complete with "no changes". If failure → fail Lorque with the subprocess stderr captured. |
| Q7 | `BobContainerAuthorizer` registration mechanism — plugin beans (one bean per module that declares its container + privileges) or central config in `application.yml`? | Lean plugin: each module declares its own, keeps coupling local. Final shape decided at implementation time. |

*Q1 (new table vs. extend Lorque), Q4 (authz on Minio bucket), Q6 (backfill source) were resolved in the "Resolved during review" section above.*

## Phased delivery

Three PRs, deployable independently. Each leaves the system in a better state than before; later phases build on earlier ones.

### Phase 1 — Generic `/bob/objects` REST + import controllers backed by Bob, for both SNOMED and LOINC

Smallest unit of work that eliminates the OOM. Does **not** introduce delta-generator yet — SNOMED imports a full snapshot just like before, just streamed through Bob and a Lorque async job. LOINC's only fix is this phase; the rest doesn't apply to it.

What lands in this PR:
- **New `BobObjectController`** under `bob/` exposing `POST/GET/PATCH/DELETE /bob/objects` and `GET /bob/objects/{uuid}/content` (the surface described in D6a). Stream-friendly — `StreamingFileUpload` for upload so bytes never sit in heap; streamed body on download. Per-container authz via `BobContainerAuthorizer` beans (D6a).
- **Authorizers registered**: `WikiBobContainerAuthorizer` (preserves existing Wiki behaviour), `SnomedBobContainerAuthorizer`, `LoincBobContainerAuthorizer`.
- **SNOMED** — `SnomedController.import(...)` rewritten as a thin endpoint that accepts either `?fromObjectId={uuid}` (use an already-stored BobObject) or a multipart file (one-step: stores via `BobObjectService` internally, then triggers the same job). Starts a Lorque job (`SnomedRF2ImportJob`), returns the `LorqueProcess`. The job uses `BobObjectService.loadContent(...)` to stream from Minio to `/tmp`, calls `SnowstormClient.createImportJob` → `uploadRF2File` reading from a `Path`, reports progress through `lorqueProcessService.reportProgress(...)`, and patches the BobObject's `meta` with the final `importStatus` and `lorqueProcessId`. Old synchronous code path deleted.
- **LOINC** — `LoincController.import(...)` (`edition-int/.../LoincController.java:34`) gets the same refactor — accepts `?fromObjectId={uuid}` or multipart, starts a Lorque job, hands a `Path` to `LoincService` (which already reads from an `InputStream`). Old synchronous code path deleted.
- **UI — Stored uploads list view.** A new Angular component renders the result of `GET /bob/objects?container={terminology}` as a filterable table on both the SNOMED and LOINC CodeSystem pages. Per-row actions: `Trigger import` (calls `POST /snomed/imports?fromObjectId={uuid}`) and `Delete` (calls `DELETE /bob/objects/{uuid}`). Top-of-page *Upload archive (no import)* button posts to `POST /bob/objects?container={terminology}`. Status polling on the import job reuses the existing `/lorque-processes/{id}/status` flow.

No new tables, no new shared service layer. Everything sits on top of `BobObjectService` and the existing `bob.object` / `bob.object_storage` schema, with new container values `"snomed"` / `"loinc"`. Wiki rows untouched.

Acceptance:
- International SNOMED edition (~1 GB) imports successfully on dev-server with current `-Xmx1800m`; LOINC release-bundle imports the same way; heap usage stays bounded; failure cases (Snowstorm down, malformed archive) surface as `lorqueProcessService.fail` instead of HTTP 500.
- `GET /bob/objects?container=wiki` returns the existing Wiki rows unchanged; Wiki page-attachment uploads/lists/deletes still work through both the existing `/pages/{id}/files` endpoints AND the new generic `/bob/objects` endpoints.
- An admin can upload an archive without triggering an import (`POST /bob/objects?container=snomed` with `edition`/`effectiveTime` keys in `meta`), and subsequent imports for the next release use it as the baseline once Phase 2 lands.

### Phase 2 — SNOMED delta-generator-tool integration

Builds on Phase 1.

- Add `delta-generator-tool` jar to the `termx-app` Docker image (multi-stage: download the released jar from IHTSDO releases, copy into the final image).
- New `DeltaGeneratorRunner` service that takes two `Path`s (baseline, new) and a target output `Path`, runs the jar via `ProcessBuilder`, streams stderr to logs, has a configurable timeout, and captures the exit code into the Lorque `result_text` on failure.
- `SnomedRF2ImportJob` gains a delta step between download and Snowstorm upload:
  - Look up the most-recent succeeded baseline for the edition: query `BobObjectService` for `container=snomed`, `meta.terminology=snomed`, `meta.edition=<…>`, `meta.importStatus=COMPLETED`, ordered by `created` desc.
  - If none → push the full snapshot (Phase 1 behaviour).
  - If found → load baseline + new to `/tmp` via `BobObjectService.loadContent(...)`, run delta-generator → `/tmp/delta.zip`, push the delta archive via Snowstorm two-step.
- Same job framework + UI; no surface change.

Acceptance: a second import of the same edition with one concept changed produces a delta archive dramatically smaller than the source ZIP, and Snowstorm shows the expected diff applied. Empty delta is a clean no-op with Lorque marked complete + "no changes" message.

This phase is **SNOMED-only.** LOINC stays on Phase 1's full-snapshot model — no equivalent diff tool exists.

### Phase 3 — Presigned upload URLs (optional / nice-to-have)

UI requests a presigned PUT URL from a new `POST /bob/objects/presigned-upload-url` endpoint (which writes a placeholder `BobObject` row and returns the Minio presigned URL + object UUID); browser PUTs the file directly to Minio; UI then notifies TermX via `POST /bob/objects/{uuid}/finalize` so the row is moved out of the placeholder state. Eliminates the last "TermX proxies a multi-hundred-MB file" path entirely. Applies to every container (SNOMED, LOINC, Wiki, future modules) — same plumbing. Could be folded into Phase 1 but tracked separately because it touches the UI and the Minio policy model (presigned URLs require a Minio policy that permits direct PUTs).

## Out of scope for this design

- Refactoring the existing dry-run scan (`/imports/scan`) — it already uses Lorque, just from a DB-blob cache rather than Minio. Migration tracked via Q3.
- Memory-efficient parsing inside `SnomedRF2ZipParser` — once delta-generator is doing the heavy lifting on disk, the scan's in-memory accumulators stop mattering for the import path. Keep scan as-is.
- Changes to other terminology imports beyond SNOMED and LOINC (FHIR CodeSystem upload, UCUM, etc.) — different code paths, different scale. Re-evaluate per-terminology if OOM reports come in.
- Keeping the current synchronous `/imports` / LOINC `/import` as a fallback — the existing flow is the bug we're fixing, not a backup worth preserving.

## Verification plan (when implementation lands)

1. **Phase 1 — SNOMED.** Local: `JAVA_OPTS=-Xmx1800m` (current dev-server) + International edition import → completes without OOM, peak RSS stays under 2 GB.
2. **Phase 1 — LOINC.** A full LOINC release-bundle import via the new endpoint completes without OOM under the same heap limits.
3. **Phase 1 — Wiki preserved.** Existing Wiki page-attachment rows in `bob.object` remain readable through both `/pages/{id}/files` (existing) and the new `GET /bob/objects?container=wiki` (new). Uploading a new Wiki attachment continues to work through `/pages/{id}/files`. No data migration needed.
4. **Phase 1 — generic Bob authz.** A user with `snomed-ct.CodeSystem.read` can `GET /bob/objects?container=snomed` but is rejected with 403 on `?container=wiki`. The Wiki authorizer keeps its existing privilege semantics.
5. **Phase 1 — stored uploads UI.** Admin uploads a SNOMED archive without triggering an import (`POST /bob/objects?container=snomed` with edition/effectiveTime in `meta`); the row appears in the UI list; *Trigger import* on that row calls `POST /snomed/imports?fromObjectId={uuid}` and starts a Lorque job without re-uploading; *Delete* removes both the row and the Minio object.
6. **Phase 1 — failure mode.** Kill Snowstorm mid-import → Lorque process moves to FAILED with the connection error in `result_text`; UI shows it. No process holding onto a stale `byte[]` in heap.
7. **Phase 2 — delta path.** With a baseline BobObject present, a normal import of the next release produces a delta archive dramatically smaller than the source ZIP; Snowstorm shows only the expected diff applied. The new BobObject is patched with `meta.importStatus=COMPLETED` and a back-reference to the Lorque process.
8. **Phase 2 — empty delta.** Re-import an identical archive → delta-generator produces an empty delta → Lorque marked complete with "no changes" message; nothing pushed to Snowstorm.
9. **Phase 2 — failure mode.** Feed delta-generator a corrupted ZIP → subprocess exits non-zero; Lorque captures stderr and marks FAILED; TermX JVM heap unaffected.
10. **Minio retention.** Trigger 4 imports for the same edition; oldest 1 is deleted by the cleanup job (retention policy = N most recent per edition, default `N=3`).
