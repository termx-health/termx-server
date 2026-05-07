# termx-server / scripts

Standalone CLI helpers that mirror the SNOMED dry-run scan and concept-usage lookup
features. Useful when you need the same output without spinning up the full
termx-server stack — ad-hoc reports, batch triage, CI checks, support workflows.

Both scripts emit JSON whose shape matches the corresponding REST responses, so the
same UI / tooling can consume the output.

## Requirements

- Python 3.9+
- For `snomed_concept_usage.py` only: `pip install psycopg2-binary`

## `snomed_rf2_scan.py` — RF2 dry-run scan (Utility 1)

Parses a SNOMED RF2 release zip and produces a change report (new / modified /
invalidated concepts, with their designations and attributes). Algorithm matches
`org.termx.snomed.integration.rf2.scan.SnomedRF2DiffEngine` line-for-line.

```text
python3 snomed_rf2_scan.py <ZIP> [--cutoff YYYYMMDD] [-o OUTPUT.json]
                                 [--branch-path MAIN] [--rf2-type SNAPSHOT|DELTA|FULL]
                                 [--mode summary|full] [-q]
```

### Mode

- `--mode summary` (default) — parses only the Concept, Description and TextDefinition files. Several times faster on a full International edition zip (the Relationship and Language-refset files dominate wall-clock time). Designations are reported with `acceptability="none"` and there are no attributes.
- `--mode full` — parses all five RF2 file kinds. Acceptability and attributes are populated.

### Examples

Scan a full International release, auto-detect cutoff (= max effectiveTime in the
Concept file, i.e. the latest release in the zip):

```sh
python3 snomed_rf2_scan.py ~/Downloads/SnomedCT_InternationalRF2_PRODUCTION_20260101T120000Z.zip \
    -o /tmp/scan.json
```

Filter changes since a specific date (e.g. everything since 2025-10-01), full mode:

```sh
python3 snomed_rf2_scan.py ~/Downloads/SnomedCT_InternationalRF2_PRODUCTION_20260101T120000Z.zip \
    --cutoff 20251001 \
    --mode full \
    -o /tmp/scan-since-20251001.json
```

### Output

- Prints stats (`conceptsAdded`, `conceptsInvalidated`, etc.) to stdout.
- Writes the full `SnomedRF2ScanResult`-shaped JSON to `--output`
  (`stats`, `newConcepts[]`, `modifiedConcepts[]`, `invalidatedConcepts[]`).
- Progress messages go to stderr; pass `-q` to silence.

## `snomed_concept_usage.py` — Concept usage lookup (Utility 2)

Given a list of SNOMED concept codes, queries the termx Postgres database for places
where they are referenced:

| Resource | Source |
|---|---|
| `CodeSystemSupplement` | `terminology.code_system_entity_version` rows in CSes whose `base_code_system='snomed-ct'` |
| `ValueSet` (rule) | `terminology.value_set_version_rule.concepts` JSONB array |
| `ValueSetExpansion` | `terminology.value_set_snapshot.expansion` JSONB array |

```text
python3 snomed_concept_usage.py [--codes "<list>" | --codes-file FILE | (stdin)]
                                [--include-modified]
                                (--dsn URL | --host… --port… --dbname… --user… --password…)
                                [-o OUTPUT.json]
```

### Connection options (in order of precedence)

1. `--dsn 'postgres://user:pass@host:5432/termx'`
2. Environment variable `DATABASE_URL`
3. `--host` / `--port` / `--dbname` / `--user` / `--password` flags
4. Standard libpq env vars `PGHOST` / `PGPORT` / `PGDATABASE` / `PGUSER` / `PGPASSWORD`

### Input options (combine freely; stdin used if none provided)

The `--codes` / `--codes-file` parsers accept any of:

- Plain text — one code per line, or comma / space / semicolon separated.
- A bare JSON array of strings or `{conceptId|code}` objects.
- A full `SnomedRF2ScanResult` JSON (the output of `snomed_rf2_scan.py` or the
  in-app dry-run download). By default only `invalidatedConcepts[].conceptId` is
  extracted; pass `--include-modified` to also include `modifiedConcepts[].conceptId`.

### Examples

Look up a small inline list against a local dev database:

```sh
python3 snomed_concept_usage.py \
    --codes "264936004, 7377003, 241185003" \
    --dsn 'postgres://termx:termx@localhost:5432/termx'
```

Feed the invalidated codes from a previous dry-run scan:

```sh
python3 snomed_concept_usage.py \
    --codes-file /tmp/scan-since-20251001.json \
    --dsn "$DATABASE_URL" \
    -o /tmp/usage.json
```

Pipe codes via stdin (e.g. from `jq`):

```sh
jq -r '.invalidatedConcepts[].conceptId' /tmp/scan.json \
  | python3 snomed_concept_usage.py --dsn "$DATABASE_URL"
```

### Output

JSON array of `SnomedConceptUsage` rows:

```json
[
  {
    "resourceType": "CodeSystemSupplement",
    "resourceId":   "snomed-est-supplement",
    "resourceVersion": null,
    "conceptCode":  "264936004",
    "location":     "concept"
  },
  {
    "resourceType": "ValueSet",
    "resourceId":   "my-procedures-vs",
    "resourceVersion": "1.0.0",
    "conceptCode":  "7377003",
    "location":     "rule"
  }
]
```

A summary by resource type is also printed to stderr (suppress with `-q`).

## Permissions

Both scripts are read-only — they never modify the database or the zip.
For `snomed_concept_usage.py` the database user only needs `SELECT` on
`terminology.code_system`, `terminology.code_system_entity_version`,
`terminology.value_set_version`, `terminology.value_set_version_rule`,
`terminology.value_set_version_rule_set`, and `terminology.value_set_snapshot`.

## When to prefer the in-app feature

- Need progress UI, auto-download, or "Proceed with import" → use the dry-run
  modal in `termx-web` (`SNOMED CodeSystem edit page → Import from RF2 → Dry run`).
- Need pre-built links from results to the matching CodeSystem / ValueSet edit
  pages → use the `SNOMED concept usage` page.
- Need a local report without a running server, or a scriptable batch run → use
  these CLIs.
