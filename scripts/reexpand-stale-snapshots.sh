#!/usr/bin/env bash
#
# Re-expand "stale" / fat ValueSet snapshots.
#
# A snapshot row in terminology.value_set_snapshot written before the
# 2026-02-16 "exclude all versions information for properties type Coding"
# change still stores Coding property values as the whole embedded entity
# (nested versions[], designations, propertyValues, sysModifiedBy,
# codeSystemEntityId, immutable, ...) plus duplicate property rows. Those
# markers never appear in the slim {code, codeSystem, version, display}
# shape, so we use them to detect stale rows.
#
# Re-expanding a value set version through POST /ts/value-sets/expand rebuilds
# the snapshot via the live decorate() path (slim + deduped) and, when the
# referenced coding values have been enriched, backfills version/display.
#
# This is deliberately an operator task, NOT a Liquibase changeset: correct
# regeneration needs the application layer, it mutates derived cache data, and
# it should run against a chosen environment under supervision — never
# automatically at deploy time. See docs/reexpand-stale-snapshots.md.
#
# Safe by default: DRY RUN unless --apply is given. Idempotent: a re-run only
# re-expands rows that are still stale.
#
# Usage:
#   scripts/reexpand-stale-snapshots.sh                 # dry run: list stale snapshots
#   scripts/reexpand-stale-snapshots.sh --apply         # re-expand them
#   scripts/reexpand-stale-snapshots.sh --apply --limit 50
#   VALUE_SET=my-vs scripts/reexpand-stale-snapshots.sh --apply   # one value set only
#
# Configuration (env vars):
#   PSQL          psql invocation (default: "psql"). Override for docker, e.g.
#                 PSQL="docker exec -i pg psql -U postgres termx"
#   DATABASE_URL  optional libpq conninfo passed to psql -d (else PG* env is used)
#   TERMX_URL     backend base URL (default: http://localhost:8200)
#   TERMX_TOKEN   bearer token with VS_READ on the target value sets (required for --apply)
#   VALUE_SET     restrict to a single value set id (optional)
#   LIMIT         max number to re-expand (optional; --limit overrides)
#
set -euo pipefail

PSQL="${PSQL:-psql}"
TERMX_URL="${TERMX_URL:-http://localhost:8200}"
TERMX_TOKEN="${TERMX_TOKEN:-}"
VALUE_SET="${VALUE_SET:-}"
LIMIT="${LIMIT:-}"
APPLY=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --apply) APPLY=true; shift ;;
    --limit) LIMIT="${2:?--limit needs a number}"; shift 2 ;;
    --value-set) VALUE_SET="${2:?--value-set needs an id}"; shift 2 ;;
    -h|--help) sed -n '2,40p' "$0"; exit 0 ;;
    *) echo "unknown argument: $1" >&2; exit 2 ;;
  esac
done

psql_q() {
  if [[ -n "${DATABASE_URL:-}" ]]; then
    $PSQL -d "$DATABASE_URL" -At -v ON_ERROR_STOP=1 -c "$1"
  else
    $PSQL -At -v ON_ERROR_STOP=1 -c "$1"
  fi
}

# Stale marker: embedded-entity fields that exist only in the fat Coding shape.
STALE_PREDICATE="(vss.expansion::text LIKE '%\"sysModifiedBy\"%' OR vss.expansion::text LIKE '%\"codeSystemEntityId\"%')"
[[ -n "$VALUE_SET" ]] && STALE_PREDICATE="$STALE_PREDICATE AND vss.value_set = '${VALUE_SET//\'/\'\'}'"

echo "== Probing terminology.value_set_snapshot for stale (fat) rows =="
psql_q "
  SELECT count(*),
         coalesce(pg_size_pretty(sum(pg_column_size(vss.expansion))::bigint), '0 bytes')
  FROM terminology.value_set_snapshot vss
  WHERE $STALE_PREDICATE;
" | awk -F'|' '{printf "stale snapshots: %s   fat bytes: %s\n", $1, $2}'

# (value_set, version) pairs to re-expand, biggest first.
LIMIT_SQL=""
[[ -n "$LIMIT" ]] && LIMIT_SQL="LIMIT ${LIMIT}"
mapfile -t ROWS < <(psql_q "
  SELECT vss.value_set || E'\t' || vsv.version || E'\t' || pg_size_pretty(pg_column_size(vss.expansion)::bigint)
  FROM terminology.value_set_snapshot vss
  JOIN terminology.value_set_version vsv ON vsv.id = vss.value_set_version_id
  WHERE $STALE_PREDICATE
  ORDER BY pg_column_size(vss.expansion) DESC
  $LIMIT_SQL;
")

if [[ ${#ROWS[@]} -eq 0 ]]; then
  echo "Nothing to do — no stale snapshots match."
  exit 0
fi

echo
printf '%-40s %-16s %s\n' "VALUE_SET" "VERSION" "SIZE"
for row in "${ROWS[@]}"; do
  IFS=$'\t' read -r vs ver size <<<"$row"
  printf '%-40s %-16s %s\n' "$vs" "$ver" "$size"
done
echo

if ! $APPLY; then
  echo "DRY RUN — ${#ROWS[@]} snapshot(s) would be re-expanded. Re-run with --apply to rebuild them."
  exit 0
fi

if [[ -z "$TERMX_TOKEN" ]]; then
  echo "ERROR: TERMX_TOKEN is required for --apply (bearer token with VS_READ)." >&2
  exit 1
fi

echo "Re-expanding ${#ROWS[@]} snapshot(s) via ${TERMX_URL}/ts/value-sets/expand ..."
ok=0; fail=0
for row in "${ROWS[@]}"; do
  IFS=$'\t' read -r vs ver _ <<<"$row"
  code=$(curl -sS -o /dev/null -w '%{http_code}' \
    -X POST "${TERMX_URL}/ts/value-sets/expand" \
    -H "Authorization: Bearer ${TERMX_TOKEN}" \
    -H "Content-Type: application/json" \
    -d "{\"valueSet\":\"${vs}\",\"valueSetVersion\":\"${ver}\"}" || echo "000")
  if [[ "$code" == "200" ]]; then
    echo "  ok    ${vs} ${ver}"
    ok=$((ok + 1))
  else
    echo "  FAIL  ${vs} ${ver} (HTTP ${code})" >&2
    fail=$((fail + 1))
  fi
done

echo
echo "Done: ${ok} re-expanded, ${fail} failed."
[[ $fail -eq 0 ]]
