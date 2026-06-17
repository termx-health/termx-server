# Re-expanding stale ValueSet snapshots

## What a "stale snapshot" is

`terminology.value_set_snapshot.expansion` is a cached JSONB array of
`ValueSetVersionConcept` objects, served back from `$expand` without re-running
the live expansion logic.

Rows written **before the 2026-02-16 change** ("exclude all versions information
for properties type Coding") store every `Coding`-typed property value as the
**whole embedded entity** — its own `versions[]`, `designations`, recursive
`propertyValues`, `sysModifiedBy`, `codeSystemEntityId`, `immutable` — plus
**duplicate property rows**. A fresh snapshot instead carries the slim
`{code, codeSystem, version, display}` shape, deduped.

Such a row is "stale/fat". It is detected by markers that exist **only** in the
embedded form: `"sysModifiedBy"` and `"codeSystemEntityId"`.

## Does this affect the API output?

- **FHIR `$expand`** — no. `ValueSetFhirMapper` applies the declared-property
  whitelist and reads codings via `asCodingValue()`, so the response is slim even
  off a stale snapshot. The only visible gap: coding `version`/`display` are empty
  for stale rows, because the embedded entity predates the coding-refresh
  enrichment.
- **Internal `/ts/value-sets/expand` read** and **stored size** — yes. These still
  carry the fat JSONB until the snapshot is rebuilt.

So this is housekeeping (storage + internal endpoint + lighting up
version/display), not a user-facing outage. There is no urgency forcing it into a
deploy.

## Why this is an operator task, not Liquibase

Correct regeneration needs the **application layer** (`decorate()` for slimming +
the coding-refresh enrichment for `version`/`display`) — a pure-SQL changeset can
only do a fragile in-place JSONB strip that still leaves `version`/`display`
empty. A changeset also runs **automatically on every environment at deploy**,
bulk-mutating the canonical published snapshots — high blast radius,
non-idempotent, hard to test against real prod JSONB. Re-expansion through the
app is controlled, per-value-set, observable and reversible. Keep it out of the
changelog.

## Probe (read-only)

Find and size the stale rows directly:

```sql
SELECT vss.id, vss.value_set, vsv.version, vss.concepts_total,
       pg_size_pretty(pg_column_size(vss.expansion)::bigint) AS expansion_size,
       vss.created_at
FROM terminology.value_set_snapshot vss
JOIN terminology.value_set_version vsv ON vsv.id = vss.value_set_version_id
WHERE vss.expansion::text LIKE '%"sysModifiedBy"%'
   OR vss.expansion::text LIKE '%"codeSystemEntityId"%'
ORDER BY pg_column_size(vss.expansion) DESC;
```

## Re-expanding

Use [`scripts/reexpand-stale-snapshots.sh`](../scripts/reexpand-stale-snapshots.sh).
It probes for stale rows and re-expands each `(value_set, version)` via
`POST /ts/value-sets/expand`, which rebuilds the snapshot through the live path.

**Safe by default** — dry run unless `--apply`. **Idempotent** — a re-run only
touches rows that are still stale.

```bash
# 1. Dry run: list stale snapshots, biggest first (no changes)
scripts/reexpand-stale-snapshots.sh

# 2. Re-expand them (needs a VS_READ token)
TERMX_URL=https://termx.example.org \
TERMX_TOKEN=<bearer> \
scripts/reexpand-stale-snapshots.sh --apply

# Useful flags
scripts/reexpand-stale-snapshots.sh --apply --limit 50      # batch
VALUE_SET=my-vs scripts/reexpand-stale-snapshots.sh --apply # one value set
```

Configuration (env): `PSQL` (default `psql`; override for docker, e.g.
`PSQL="docker exec -i pg psql -U postgres termx"`), `DATABASE_URL` (optional libpq
conninfo; otherwise standard `PG*` env), `TERMX_URL` (default
`http://localhost:8200`), `TERMX_TOKEN` (bearer with `VS_READ`).

### Backfilling coding `version` / `display`

Re-expansion copies whatever is on the stored `entity_property_value` coding
values. If those were never enriched, `version`/`display` stay empty. To fill
them, run the coding-refresh **first**, then re-expand:

```bash
curl -X POST "$TERMX_URL/ts/code-systems/{codeSystem}/versions/{version}/refresh-coding-values" \
     -H "Authorization: Bearer $TERMX_TOKEN" -H "Content-Type: application/json" -d '{}'
```

## Verifying

Re-run the probe (or the script's dry run): a rebuilt snapshot no longer matches
the stale markers, so the count drops and the reclaimed bytes show up as a smaller
`value_set_snapshot` footprint.
