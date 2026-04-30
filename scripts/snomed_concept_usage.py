#!/usr/bin/env python3
"""
Standalone CLI runner that mirrors org.termx.snomed.integration.usage.SnomedConceptUsageRepository.

Given a list of SNOMED concept codes, queries the termx Postgres database for places
where those codes are referenced:

  1. CodeSystem supplements   -> terminology.code_system_entity_version (in CSes whose
                                 base_code_system='snomed-ct')
  2. ValueSet rules           -> terminology.value_set_version_rule.concepts (JSONB array)
  3. ValueSet expansions      -> terminology.value_set_snapshot.expansion (JSONB array)

Output JSON shape matches the SnomedConceptUsage REST endpoint
(POST /snomed/concept-usage), so the same UI / tooling can consume it.

Requires: psycopg2-binary  (pip install psycopg2-binary)
"""
import argparse
import json
import os
import sys
from typing import List

try:
    import psycopg2
    import psycopg2.extras
except ImportError:
    sys.stderr.write("ERROR: psycopg2 is required. Install with: pip install psycopg2-binary\n")
    raise SystemExit(2)


SNOMED_CS = "snomed-ct"
SNOMED_URI = "http://snomed.info/sct"


SQL_SUPPLEMENTS = """
select 'CodeSystemSupplement' as resource_type,
       cs.id                  as resource_id,
       null::text             as resource_version,
       cev.code               as concept_code,
       'concept'              as location
  from terminology.code_system_entity_version cev
  join terminology.code_system cs on cs.id = cev.code_system
 where cs.base_code_system = %s
   and cev.sys_status = 'A'
   and cs.sys_status  = 'A'
   and cev.code = any(%s)
"""

SQL_VS_RULES = """
select 'ValueSet'                                           as resource_type,
       vsv.value_set                                        as resource_id,
       vsv.version                                          as resource_version,
       coalesce(c->'concept'->>'code', c->>'code')          as concept_code,
       'rule'                                               as location
  from terminology.value_set_version_rule vsr
  join terminology.value_set_version_rule_set vsrs on vsrs.id = vsr.rule_set_id
  join terminology.value_set_version vsv on vsv.id = vsrs.value_set_version_id
  cross join lateral jsonb_array_elements(coalesce(vsr.concepts, '[]'::jsonb)) c
 where vsr.sys_status  = 'A'
   and vsrs.sys_status = 'A'
   and vsv.sys_status  = 'A'
   and (vsr.code_system = %s
        or coalesce(c->'concept'->>'codeSystem', c->>'codeSystem') = %s
        or coalesce(c->'concept'->>'codeSystemUri', c->>'codeSystemUri') = %s)
   and coalesce(c->'concept'->>'code', c->>'code') = any(%s)
"""

SQL_VS_EXPANSIONS = """
select 'ValueSetExpansion'                                   as resource_type,
       vss.value_set                                         as resource_id,
       vsv.version                                           as resource_version,
       coalesce(c->'concept'->>'code', c->>'code')           as concept_code,
       'expansion'                                           as location
  from terminology.value_set_snapshot vss
  join terminology.value_set_version vsv on vsv.id = vss.value_set_version_id
  cross join lateral jsonb_array_elements(coalesce(vss.expansion, '[]'::jsonb)) c
 where vss.sys_status = 'A'
   and vsv.sys_status = 'A'
   and (coalesce(c->'concept'->>'codeSystem', c->>'codeSystem') = %s
        or coalesce(c->'concept'->>'codeSystemUri', c->>'codeSystemUri') = %s)
   and coalesce(c->'concept'->>'code', c->>'code') = any(%s)
"""


def parse_input(raw: str, include_modified: bool = False) -> List[str]:
    """Accept plain text (any separator), bare JSON array, or a full SnomedRF2ScanResult JSON."""
    raw = (raw or "").strip()
    if not raw:
        return []
    if raw.startswith("{") or raw.startswith("["):
        try:
            parsed = json.loads(raw)
        except json.JSONDecodeError:
            parsed = None
        if parsed is not None:
            codes = []
            _walk(parsed, codes, include_modified)
            if codes:
                return _dedup(codes)
    # fallback: split on whitespace / comma / semicolon
    import re
    return _dedup([t for t in re.split(r"[\s,;]+", raw) if t])


def _walk(node, out: List[str], include_modified: bool, depth: int = 0):
    if node is None:
        return
    if isinstance(node, list):
        for item in node:
            _walk(item, out, include_modified, depth + 1)
        return
    if isinstance(node, str):
        if node.isdigit():
            out.append(node)
        return
    if not isinstance(node, dict):
        return
    # Top-level scan-result shape
    if depth == 0 and ("invalidatedConcepts" in node or "modifiedConcepts" in node or "newConcepts" in node):
        for c in node.get("invalidatedConcepts") or []:
            cid = (c or {}).get("conceptId")
            if cid:
                out.append(cid)
        if include_modified:
            for c in node.get("modifiedConcepts") or []:
                cid = (c or {}).get("conceptId")
                if cid:
                    out.append(cid)
        return
    cid = node.get("conceptId") or node.get("code")
    if isinstance(cid, str) and cid:
        out.append(cid)
    for v in node.values():
        _walk(v, out, include_modified, depth + 1)


def _dedup(codes: List[str]) -> List[str]:
    seen, out = set(), []
    for c in codes:
        c = c.strip()
        if c and c not in seen:
            seen.add(c)
            out.append(c)
    return out


def collect_codes(args) -> List[str]:
    codes: List[str] = []
    if args.codes:
        codes += parse_input(args.codes, args.include_modified)
    if args.codes_file:
        with open(args.codes_file, "r") as f:
            codes += parse_input(f.read(), args.include_modified)
    if not codes and not sys.stdin.isatty():
        codes += parse_input(sys.stdin.read(), args.include_modified)
    return _dedup(codes)


def build_dsn(args) -> str:
    if args.dsn:
        return args.dsn
    if os.environ.get("DATABASE_URL"):
        return os.environ["DATABASE_URL"]
    parts = []
    if args.host or os.environ.get("PGHOST"):
        parts.append(f"host={args.host or os.environ['PGHOST']}")
    if args.port or os.environ.get("PGPORT"):
        parts.append(f"port={args.port or os.environ['PGPORT']}")
    if args.dbname or os.environ.get("PGDATABASE"):
        parts.append(f"dbname={args.dbname or os.environ['PGDATABASE']}")
    if args.user or os.environ.get("PGUSER"):
        parts.append(f"user={args.user or os.environ['PGUSER']}")
    if args.password or os.environ.get("PGPASSWORD"):
        parts.append(f"password={args.password or os.environ['PGPASSWORD']}")
    if not parts:
        raise SystemExit(
            "ERROR: no database connection info. Pass --dsn 'postgres://…', set "
            "DATABASE_URL, or pass --host/--port/--dbname/--user/--password."
        )
    return " ".join(parts)


def query_all(dsn: str, codes: List[str]):
    if not codes:
        return []
    rows = []
    with psycopg2.connect(dsn) as conn:
        with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
            for sql, params in (
                (SQL_SUPPLEMENTS, (SNOMED_CS, codes)),
                (SQL_VS_RULES, (SNOMED_CS, SNOMED_CS, SNOMED_URI, codes)),
                (SQL_VS_EXPANSIONS, (SNOMED_CS, SNOMED_URI, codes)),
            ):
                cur.execute(sql, params)
                rows.extend(cur.fetchall())
    return [
        {
            "resourceType": r["resource_type"],
            "resourceId": r["resource_id"],
            "resourceVersion": r["resource_version"],
            "conceptCode": r["concept_code"],
            "location": r["location"],
        }
        for r in rows
    ]


def main():
    p = argparse.ArgumentParser(
        description=__doc__,
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    src = p.add_argument_group("input (any combination, plus stdin)")
    src.add_argument("--codes", help="Inline code list. Whitespace/comma/semicolon separated, "
                                     "or a JSON array, or a full SnomedRF2ScanResult JSON.")
    src.add_argument("--codes-file", help="Path to a file containing codes (text or JSON).")
    src.add_argument("--include-modified", action="store_true",
                     help="When the input is a SnomedRF2ScanResult, also include conceptIds from "
                          "modifiedConcepts (default: only invalidatedConcepts).")

    db = p.add_argument_group("database connection")
    db.add_argument("--dsn", help="libpq connection string (e.g. 'postgres://user:pass@host:5432/termx'). "
                                  "Overrides individual options and DATABASE_URL.")
    db.add_argument("--host"); db.add_argument("--port")
    db.add_argument("--dbname"); db.add_argument("--user"); db.add_argument("--password")

    out = p.add_argument_group("output")
    out.add_argument("-o", "--output", default="-",
                     help="Output JSON path. '-' (default) writes to stdout.")
    out.add_argument("-q", "--quiet", action="store_true", help="Suppress progress logging on stderr.")

    args = p.parse_args()
    log = (lambda msg: print(msg, file=sys.stderr)) if not args.quiet else (lambda msg: None)

    codes = collect_codes(args)
    if not codes:
        raise SystemExit("ERROR: no codes provided. Pass --codes, --codes-file, or pipe via stdin.")
    log(f"[usage] {len(codes)} code(s) to look up")

    dsn = build_dsn(args)
    rows = query_all(dsn, codes)
    log(f"[usage] {len(rows)} reference(s) found")

    payload = json.dumps(rows, indent=2)
    if args.output == "-":
        sys.stdout.write(payload + "\n")
    else:
        with open(args.output, "w") as f:
            f.write(payload)
        log(f"[usage] wrote results to {args.output}")

    by_type = {}
    for r in rows:
        by_type[r["resourceType"]] = by_type.get(r["resourceType"], 0) + 1
    if not args.quiet:
        print("\nReferences by resource type:", file=sys.stderr)
        for k, v in sorted(by_type.items()):
            print(f"  {k}: {v}", file=sys.stderr)


if __name__ == "__main__":
    main()
