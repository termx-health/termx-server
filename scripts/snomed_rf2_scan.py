#!/usr/bin/env python3
"""
Standalone CLI runner that mirrors org.termx.snomed.integration.rf2.scan.SnomedRF2DiffEngine.

Use this when you need to dry-run-scan a SNOMED RF2 release zip without spinning up
the full termx-server stack (e.g. for triage, ad-hoc reports, or in CI).

Algorithm — same as the in-app dry-run:
  - filter rows whose effectiveTime >= cutoff (default: max effectiveTime in Concept file)
  - for each conceptId touched in the change-set:
      concept row active=0  -> INVALIDATED
      concept row active=1  -> NEW
      only desc/rel rows    -> MODIFIED

Output JSON shape matches SnomedRF2ScanResult so the same UI / tooling can consume it.
"""
import argparse
import datetime
import io
import json
import sys
import zipfile
from collections import defaultdict


FSN = "900000000000003001"
SYNONYM = "900000000000013009"
TEXT_DEFINITION = "900000000000550004"
PREFERRED = "900000000000548007"
ACCEPTABLE = "900000000000549004"


def open_filtered(zf, member, cutoff):
    """Yield TSV rows (lists of strings) where effectiveTime[col 1] >= cutoff. Skip header."""
    with zf.open(member) as raw:
        for i, line in enumerate(io.TextIOWrapper(raw, encoding="utf-8", newline="")):
            if i == 0:
                continue
            line = line.rstrip("\r\n")
            if not line:
                continue
            parts = line.split("\t")
            et = parts[1] if len(parts) > 1 else ""
            if cutoff and et < cutoff:
                continue
            yield parts


def read_concepts(zf, member, cutoff):
    rows = []
    for p in open_filtered(zf, member, cutoff):
        if len(p) < 5:
            continue
        rows.append({
            "id": p[0], "effectiveTime": p[1],
            "active": p[2] == "1", "moduleId": p[3], "definitionStatusId": p[4],
        })
    return rows


def read_descriptions(zf, member, cutoff, text_def):
    rows = []
    for p in open_filtered(zf, member, cutoff):
        if len(p) < 9:
            continue
        rows.append({
            "id": p[0], "effectiveTime": p[1], "active": p[2] == "1",
            "moduleId": p[3], "conceptId": p[4], "languageCode": p[5],
            "typeId": p[6], "term": p[7], "caseSignificanceId": p[8],
            "textDefinition": text_def,
        })
    return rows


def read_relationships(zf, member, cutoff):
    rows = []
    for p in open_filtered(zf, member, cutoff):
        if len(p) < 10:
            continue
        try:
            grp = int(p[6])
        except ValueError:
            grp = None
        rows.append({
            "id": p[0], "effectiveTime": p[1], "active": p[2] == "1",
            "moduleId": p[3], "sourceId": p[4], "destinationId": p[5],
            "relationshipGroup": grp, "typeId": p[7],
            "characteristicTypeId": p[8], "modifierId": p[9],
        })
    return rows


def read_language_refset(zf, member, cutoff):
    rows = []
    for p in open_filtered(zf, member, cutoff):
        if len(p) < 7:
            continue
        rows.append({
            "id": p[0], "effectiveTime": p[1], "active": p[2] == "1",
            "moduleId": p[3], "refsetId": p[4],
            "referencedComponentId": p[5], "acceptabilityId": p[6],
        })
    return rows


def latest_per_id(rows):
    """Keep only the latest-effectiveTime row per id (defensive against FULL files)."""
    out = {}
    for r in rows:
        ex = out.get(r["id"])
        if ex is None or ex["effectiveTime"] <= r["effectiveTime"]:
            out[r["id"]] = r
    return list(out.values())


def map_acceptability(a):
    if a == PREFERRED:
        return "preferred"
    if a == ACCEPTABLE:
        return "acceptable"
    return a or "none"


def map_type(d):
    if d.get("textDefinition"):
        return "textDefinition"
    if d["typeId"] == FSN:
        return "fully-specified-name"
    if d["typeId"] == SYNONYM:
        return "synonym"
    if d["typeId"] == TEXT_DEFINITION:
        return "textDefinition"
    return d["typeId"]


def find_member(names, prefix, contains=None):
    for n in names:
        base = n.rsplit('/', 1)[-1]
        if base.startswith(prefix) and (contains is None or contains in n):
            return n
    return None


def classify(zip_path, cutoff, branch_path="MAIN", rf2_type="SNAPSHOT", verbose=True):
    log = (lambda msg: print(msg, file=sys.stderr)) if verbose else (lambda msg: None)
    log(f"[scan] opening {zip_path}, cutoff effectiveTime >= {cutoff or '(none)'}")
    with zipfile.ZipFile(zip_path) as zf:
        names = zf.namelist()
        c_path = find_member(names, "sct2_Concept_Snapshot_", "Snapshot/Terminology/")
        d_path = find_member(names, "sct2_Description_Snapshot", "Snapshot/Terminology/")
        td_path = find_member(names, "sct2_TextDefinition_Snapshot", "Snapshot/Terminology/")
        r_path = find_member(names, "sct2_Relationship_Snapshot_", "Snapshot/Terminology/")
        lr_path = find_member(names, "der2_cRefset_LanguageSnapshot", "Snapshot/Refset/Language/")
        log(f"[scan]   concept:        {c_path}")
        log(f"[scan]   description:    {d_path}")
        log(f"[scan]   text-def:       {td_path}")
        log(f"[scan]   relationship:   {r_path}")
        log(f"[scan]   language-refset:{lr_path}")

        if c_path is None:
            raise SystemExit("ERROR: no sct2_Concept_Snapshot_*.txt found in zip — is this an RF2 release?")

        # If no cutoff supplied, use max effectiveTime in concept file (the "this release" set).
        if not cutoff:
            with zf.open(c_path) as raw:
                effs = []
                for i, line in enumerate(io.TextIOWrapper(raw, encoding="utf-8", newline="")):
                    if i == 0:
                        continue
                    parts = line.rstrip("\r\n").split("\t")
                    if len(parts) > 1 and parts[1]:
                        effs.append(parts[1])
            cutoff = max(effs) if effs else "00000000"
            log(f"[scan] auto-cutoff (max concept effectiveTime) = {cutoff}")

        concepts = read_concepts(zf, c_path, cutoff)
        log(f"[scan] concept rows post-cutoff: {len(concepts)}")
        descriptions = []
        if d_path:
            descriptions += read_descriptions(zf, d_path, cutoff, False)
        if td_path:
            descriptions += read_descriptions(zf, td_path, cutoff, True)
        log(f"[scan] description+textdef rows post-cutoff: {len(descriptions)}")
        relationships = read_relationships(zf, r_path, cutoff) if r_path else []
        log(f"[scan] relationship rows post-cutoff: {len(relationships)}")
        lang = read_language_refset(zf, lr_path, cutoff) if lr_path else []
        log(f"[scan] language-refset rows post-cutoff: {len(lang)}")

    concepts = latest_per_id(concepts)
    descriptions = latest_per_id(descriptions)
    relationships = latest_per_id(relationships)

    acceptability, et_for = {}, {}
    for r in lang:
        if not r["active"]:
            continue
        rid = r["referencedComponentId"]
        if rid not in et_for or et_for[rid] <= r["effectiveTime"]:
            et_for[rid] = r["effectiveTime"]
            acceptability[rid] = map_acceptability(r["acceptabilityId"])

    concept_by_code = {c["id"]: c for c in concepts}
    desc_by_concept = defaultdict(list)
    for d in descriptions:
        desc_by_concept[d["conceptId"]].append(d)
    rel_by_source = defaultdict(list)
    for r in relationships:
        rel_by_source[r["sourceId"]].append(r)

    touched = set()
    touched.update(concept_by_code.keys())
    touched.update(desc_by_concept.keys())
    touched.update(rel_by_source.keys())

    new_concepts, modified_concepts, invalidated_concepts = [], [], []

    def designation(d):
        return {
            "descriptionId": d["id"], "term": d["term"],
            "type": map_type(d), "language": d["languageCode"],
            "acceptability": acceptability.get(d["id"], "none"),
            "active": d["active"], "effectiveTime": d["effectiveTime"],
        }

    def attribute(r):
        return {
            "relationshipId": r["id"], "typeId": r["typeId"],
            "destinationId": r["destinationId"],
            "relationshipGroup": r["relationshipGroup"],
            "characteristicTypeId": r["characteristicTypeId"],
            "modifierId": r["modifierId"],
            "active": r["active"], "effectiveTime": r["effectiveTime"],
        }

    for cid in touched:
        crow = concept_by_code.get(cid)
        ds = desc_by_concept.get(cid, [])
        rs = rel_by_source.get(cid, [])
        if crow and not crow["active"]:
            invalidated_concepts.append({
                "conceptId": cid,
                "effectiveTime": crow["effectiveTime"],
                "moduleId": crow["moduleId"],
                "designations": [designation(d) for d in ds],
            })
        elif crow:
            new_concepts.append({
                "conceptId": cid,
                "effectiveTime": crow["effectiveTime"],
                "moduleId": crow["moduleId"],
                "definitionStatusId": crow["definitionStatusId"],
                "designations": [designation(d) for d in ds if d["active"]],
                "attributes": [attribute(r) for r in rs if r["active"]],
            })
        else:
            modified_concepts.append({
                "conceptId": cid,
                "addedDesignations": [designation(d) for d in ds if d["active"]],
                "removedDesignations": [designation(d) for d in ds if not d["active"]],
                "addedAttributes": [attribute(r) for r in rs if r["active"]],
                "removedAttributes": [attribute(r) for r in rs if not r["active"]],
            })

    stats = {
        "conceptsAdded": len(new_concepts),
        "conceptsModified": len(modified_concepts),
        "conceptsInvalidated": len(invalidated_concepts),
        "descriptionsAdded": sum(1 for d in descriptions if d["active"]),
        "descriptionsInvalidated": sum(1 for d in descriptions if not d["active"]),
        "relationshipsAdded": sum(1 for r in relationships if r["active"]),
        "relationshipsInvalidated": sum(1 for r in relationships if not r["active"]),
    }

    return {
        "branchPath": branch_path,
        "rf2Type": rf2_type,
        "releaseEffectiveTime": max((c["effectiveTime"] for c in concepts), default=cutoff),
        "scannedAt": datetime.datetime.now(datetime.timezone.utc).isoformat(),
        "uploadCacheId": None,
        "stats": stats,
        "newConcepts": new_concepts,
        "modifiedConcepts": modified_concepts,
        "invalidatedConcepts": invalidated_concepts,
    }


def main():
    p = argparse.ArgumentParser(
        description=__doc__,
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    p.add_argument("zip", help="Path to a SNOMED RF2 release zip (International or Edition).")
    p.add_argument("--cutoff", default=None,
                   help="Filter rows where effectiveTime >= this YYYYMMDD value. "
                        "If omitted, uses the max effectiveTime in the Concept file (the latest release in the zip).")
    p.add_argument("-o", "--output", default="snomed-rf2-scan.json",
                   help="Output JSON path (default: snomed-rf2-scan.json).")
    p.add_argument("--branch-path", default="MAIN", help="Branch path label for the report.")
    p.add_argument("--rf2-type", default="SNAPSHOT", choices=("SNAPSHOT", "DELTA", "FULL"),
                   help="Label for the report (algorithm is identical for all three).")
    p.add_argument("-q", "--quiet", action="store_true", help="Suppress progress logging on stderr.")
    args = p.parse_args()

    result = classify(args.zip, args.cutoff, args.branch_path, args.rf2_type, verbose=not args.quiet)

    with open(args.output, "w") as f:
        json.dump(result, f)

    s = result["stats"]
    print()
    print(f"Branch: {result['branchPath']}  Type: {result['rf2Type']}  Release: {result['releaseEffectiveTime']}")
    print(f"Cutoff: effectiveTime >= {args.cutoff or result['releaseEffectiveTime']}")
    print()
    print("Stats:")
    for k, v in s.items():
        print(f"  {k}: {v}")
    print()
    print(f"Wrote full result to {args.output}")


if __name__ == "__main__":
    main()
