# LOINC Release Import and Management

## Description

This feature lets terminology managers upload LOINC release zips once, then re-run imports against the stored archive without re-uploading the file — typically a 100 MB+ download from Regenstrief. Each archive carries its release version (`2.82`, `2.81`, …) and translation language as Bob metadata, so the import page surfaces them as a version picker rather than a free-text input. Per-CSV slot mapping is preselected from the archive's actual contents, and the heavy parse + persistence runs as a background Lorque job.

It replaces the previous flow where admins had to re-upload the eight raw CSV files (Parts, LoincPartLink_Primary, LoincPartLink_Supplementary, PanelsAndForms, AnswerList, LoincAnswerListLink, LoincUniversalLabOrdersValueSet, language-specific LinguisticVariant) every time they wanted to retry or change a single mapping.

**Key capabilities**

- **Archive storage** in the existing Bob (`bob.object` / `bob.object_storage`) layer under container `loinc`, with `meta.version` and `meta.language` tags. Multi-hundred-MB zips stream straight to Minio; the JVM never holds the full file in heap.
- **Version select** on the import page — populated from the distinct `meta.version` values across stored archives. Picking a version auto-resolves the most-recently-uploaded archive for that version; admins can override via a secondary "LOINC archive" picker when more than one archive shares a version.
- **Auto version detection on upload** — pulled from the filename pattern `Loinc_<version>.zip` (frontend regex) with a server-side fallback that scans the archive for `Loinc_<version>_DifferenceReport.pdf` so archives whose outer filename doesn't reveal the version still get tagged.
- **Per-slot file mapping** — listing endpoint walks the zip's central directory and returns every `.csv` entry plus an auto-dispatched slot suggestion (Parts / Terminology / Supplementary properties / Panels and forms / Answer list / Answer list link / Order-observation / Translations). The translations slot's suggestion is keyed off the requested language: any entry whose basename starts with the language code and ends with `LinguisticVariant` matches (e.g. `etEE10LinguisticVariant.csv` for Estonian).
- **Override before import** — every slot is preselected from the suggestion but the admin can swap in a custom CSV from any other zip entry, or leave it on "(none — use server default)" to let the auto-dispatch fall through.
- **Streaming import** — the server spools the chosen Bob archive to a local temp file, unpacks each CSV by name via `LoincZipReader`, and feeds the existing `LoincService.importLoinc` pipeline as a Lorque job. Each CSV is decompressed into byte arrays one at a time, never the whole archive.
- **Per-phase progress logging** under the `loinc-import:` prefix — admins (or grep) can see wall-clock for each parse step (answer-list, parts, concepts+associations+answer-list-link+order-observation, linguistic variants) and each persistence step (answer-list code-system, map-set, parts, concepts).
- **Independent code systems** — answer-lists, parts, and the core LOINC concept set are saved as three distinct code systems (`loinc-answer-list`, `loinc-part`, `loinc`) so consumers can subscribe to or version each one separately.

## Configuration

### Properties

LOINC piggy-backs on the existing Bob + multipart limits; no LOINC-specific knobs.

| Property | Default | Description |
|---|---|---|
| `bob.minio.url` / `bob.minio.access-key` / `bob.minio.secret-key` | (deployment) | Minio endpoint + credentials for archive storage. |
| `micronaut.server.max-request-size` | 600 MB | Hard cap on multipart upload size — LOINC release zips are ~100 MB. |
| `micronaut.server.multipart.max-file-size` | 600 MB | Same. |

### Privileges

| Privilege | Used for |
|---|---|
| `loinc.CodeSystem.read` | View the LOINC import page, list stored archives, fetch slot mappings. |
| `loinc.CodeSystem.write` | Upload a new archive, delete one, trigger an import, open a code system after a successful run. |

## Use-Cases

### Scenario 1: First-time import of a new LOINC release

A new LOINC release ships (`Loinc_2.82.zip`). The terminology manager opens the LOINC import page, picks Estonian as the language, clicks "Choose file" in the Stored Archives card, and selects the downloaded zip. The frontend immediately detects the version from the filename and pre-fills the version field. After clicking Upload, the archive streams to Minio with `meta = { version: "2.82", language: "et" }`. The version dropdown above refreshes, the new "2.82" entry appears and is auto-selected, the archive's slot mapping is fetched, and all eight per-slot selects pre-populate with the suggested CSVs (including `etEE10LinguisticVariant.csv` for Translations). The admin reviews, clicks Import, and the background job runs end-to-end. The success notification links straight to the imported code system.

### Scenario 2: Retry a failed import without re-uploading

A previous import failed mid-way (e.g. a transient DB issue). The admin returns to the import page, opens the version dropdown, picks 2.82, and clicks Import again — the same Bob-stored archive is reused, no second upload of a 100 MB file.

### Scenario 3: Override a single CSV with a corrected file

The admin needs to test a corrected `Part.csv` Regenstrief sent out-of-band. They re-zip the corrected file into the original archive, upload it, and on the import page the Parts slot stays preselected to `Loinc_2.82/AccessoryFiles/PartFile/Part.csv` — but they could equally pick a different file from the same zip via the dropdown.

### Scenario 4: Multi-version coexistence

The team archives historical versions (2.76, 2.79, 2.82) by uploading each one. The version dropdown lists all three; picking any one switches the form to that version's archive, slot mapping, and language-specific translation file. The Stored archives card lists all uploads with their version tags and a per-row Open action to load that specific archive into the form.

## Implementation Highlights

### Backend

| File | Purpose |
|---|---|
| `edition-int/.../loinc/LoincController.java` | Endpoints: `POST /loinc/import/from-archive`, `GET /loinc/archives/{uuid}/files`, legacy `POST /loinc/import`. |
| `edition-int/.../loinc/LoincImportFromArchiveService.java` | Spools archive Bob → temp file, unpacks CSVs via `LoincZipReader`, hands off to `LoincService.importLoinc`. |
| `edition-int/.../loinc/utils/LoincZipReader.java` | Streams a LOINC zip — `unpack(stream, language, fileMap)` returns the eight CSVs by slot; `describe(stream, language)` lists all `.csv` entries with auto-dispatch slot suggestion (prefix-match for translations: `<lang>...LinguisticVariant`); `detectVersion(stream)` extracts the version from `Loinc_<version>_DifferenceReport.pdf`. |
| `edition-int/.../loinc/utils/LoincArchiveContents.java` | Response DTO for the listing endpoint — entries + detectedVersion. |
| `edition-int/.../loinc/LoincBobContainerAuthorizer.java` | Per-container Bob authz: maps `loinc` container CRUD to `loinc.CodeSystem.*` privileges. |
| `edition-int/.../loinc/LoincService.java` | Parses each CSV, builds in-memory part/concept models, delegates to `CodeSystemImportProvider`. Each phase logs wall-clock under the `loinc-import:` prefix. |
| `terminology/.../codesystem/entity/CodeSystemEntityRepository.java` | `batchUpsert` chunks `INSERT … VALUES (?,?),(?,?),…` at 25 000 rows per query so the LOINC answer-list step (~33 k entities) doesn't exceed PostgreSQL's 65 535-parameter `PreparedStatement` limit. |

### Frontend

| File | Purpose |
|---|---|
| `app/src/app/integration/import/loinc/loinc-import.component.ts` / `.html` | The import page. Drives the version select, the per-slot file picks, and posts to `/loinc/import/from-archive`. |
| `app/src/app/sys/_lib/bob/components/bob-archives.component.ts` | Reusable "Stored archives" card embedded on the import page. `(fileSelected)` fires on file pick (used to auto-detect version from filename); `(uploaded)` fires after a successful upload so the version dropdown can refresh and auto-select the new entry. |

### Parsing optimisations

The CSV-to-model parsing has been tuned to keep the Java-side work to a few seconds even on full LOINC releases:

- Column-name → index resolved **once per CSV** (small `Idx` helper). Replaces the previous per-row `headers.indexOf("X")` linear scans (~10 million scans on `LoincPartLink_Primary.csv` + `_Supplementary.csv` alone).
- Incoming `Pair<String, byte[]>` file list indexed into a `Map<slot, bytes>` once at the top of `importLoinc` so each phase doesn't re-walk eight pairs.
- Single-pass concept building (no `groupingBy` → entrySet → map) and single-pass answer-list building (folds three independent collectors into one row walk).
- Linguistic-variants phase builds a per-concept `partName → partCode` map on first hit (cached for the row) so the seven part-name lookups become `O(1)`. A small `Set<partCode|lang>` de-dupes redundant `HashMap.put` calls when the same part code recurs across thousands of concepts.

### Measured timings (LOINC 2.82, Estonian, fresh import on local dev DB)

```
loinc-import: parsed answer-list (22 146 answers, 4 955 lists, 456 SNOMED mappings) in 71 ms
loinc-import: parsed 74 087 parts in 52 ms
loinc-import: parsed 109 325 concepts (terminology + associations + answer-list-link + order-observation) in 1 863 ms
loinc-import: applied linguistic variants in 121 ms
loinc-import: saved answer-list code-system in 24 970 ms
loinc-import: saved answer-list map-set (456 mappings) in 151 ms
loinc-import: saved 74 087 parts in 62 510 ms
loinc-import: saved 109 325 concepts in 279 620 ms
loinc-import: total 369 359 ms
```

Java-side parse + transform: **~2.1 s** for ~320 k input rows. End-to-end import: **~6.2 minutes** dominated by the DB writes of 1.4 M property values across 109 k concepts.

## Operational Notes

- **Idempotent re-runs** — re-importing the same archive against an existing code-system goes through the same `CodeSystemImportProvider.importCodeSystem` path which upserts by code; safe to retry.
- **No automatic concept-property refresh** — references inside other code-systems' property values (e.g. `LOINC-CODE` properties on EHR concepts) still carry the LOINC version they were saved with. See `coding-value-refresh` feature for the per-concept / per-CS-version refresh button.
- **Cache retention** — Bob archives are kept indefinitely; cleanup is manual via the Stored archives card's Delete action.
- **Translation files** — only the slot matching the requested language gets ingested; other `<lang>...LinguisticVariant.csv` entries inside the same zip are listed in the dropdown but not preselected.
- **Pre-existing latent fix** — `LoincMapper.toProperties` was rebuilding the property list via `Stream.toList()` (unmodifiable in Java 16+) then calling `.add(DISPLAY)` / `.add(KEY_WORDS)` on it. Previously masked by the upstream parameter-limit crash in `CodeSystemEntityRepository.batchUpsert`; now collects into a mutable `ArrayList`.
