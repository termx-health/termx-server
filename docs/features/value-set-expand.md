# FHIR `ValueSet/$expand`

**Feature:** Paged, filtered `$expand` over stored ValueSets, inline ValueSets posted in the request body, **implicit ValueSets synthesised over any stored CodeSystem**, and SNOMED CT implicit-ValueSet URLs (`?fhir_vs[=…]`).
**Spans:** [`terminology`](../../terminology) module (`ValueSetExpandOperation`) + delegation to [`snomed`](../../snomed) module (`SnomedValueSetExpandProvider` → Snowstorm).
**Introduced:** [termx-server#145](https://github.com/termx-health/termx-server/pull/145) — paging/filter fix on stored-VS path, SNOMED implicit-VS URL routing. Extended in [#146](https://github.com/termx-health/termx-server/pull/146) — synthetic VS over any stored CodeSystem URI (`http://loinc.org/answer-list`, `http://loinc.org/answer-list|2.82`, …).

---

## 1. Description

`POST /fhir/ValueSet/$expand` (and the instance-level `POST /fhir/ValueSet/{id}/$expand`) returns a `ValueSet` resource whose `expansion.contains` lists the codes selected by the value set. TermX accepts four forms of input, resolved in this priority order:

| # | Path | How the client identifies the VS | Where the concepts come from |
|---|---|---|---|
| 1 | **Inline-VS** | `valueSet` parameter carries a `ValueSet` resource constructed by the client | `ValueSetVersionConceptRepository.expandFromJson(...)` — Postgres function `value_set_expand_jsonb` resolves `compose.include[].system + filter + concept` directly |
| 2 | **SNOMED implicit-VS** | `url` is `http://snomed.info/sct[/<edition>/version/<date>]?fhir_vs[=…]` | `SnomedValueSetExpandProvider.ruleExpand(...)` — calls Snowstorm via `SnomedService.searchConcepts(ecl=…, branch=…)` |
| 3 | **Stored-VS** | `url` matches a `ValueSet.uri` already stored in TermX, or the operation runs at instance level (`/ValueSet/{id}/$expand`) | `ValueSetVersionConceptService.expand(...)` — snapshot persisted by TermX (re-uses cache when current) |
| 4 | **Implicit VS over a stored CodeSystem** | `url` matches a `CodeSystem.uri`; no matching stored VS exists | Synthesises an inline ValueSet with `compose.include[].system=<url>` (+ optional `version`) and routes through path 1 |

A request that resolves none of the four returns `404 NOTFOUND`.

All paths apply the same downstream parameter set: `offset`, `count`, `filter`, `displayLanguage`, `defaultLanguage`, `includeDesignations`, `activeOnly`, `excludeNested`. Paging is applied **after** filtering, per the FHIR spec.

## 2. Configuration

No configuration for the stored or inline paths.

The SNOMED implicit-VS path requires the `snomed` module to be on the deployment classpath (so `SnomedValueSetExpandProvider` registers as a `ValueSetExternalExpandProvider` bean) **and** a reachable Snowstorm instance configured in `application.yml`:

```yaml
snomed:
  url: http://localhost:8081/snowstorm/snomed-ct
```

When the SNOMED provider isn't on the classpath, requests to `http://snomed.info/sct?fhir_vs…` return `501 NOTSUPPORTED` with `OperationOutcome.diagnostics: "SNOMED expansion provider is not configured: …"`. The stored-VS and inline-VS paths still work without Snowstorm.

The `snomed-module` CodeSystem must contain entries with `moduleId` and `branchPath` property values for every SNOMED edition you want to expand against — the provider's `loadModules()` reads it to map `/sct/<sctid>/version/<YYYYMMDD>` URIs onto Snowstorm branch paths.

## 3. Supported parameters

| Parameter | Type | Notes |
|---|---|---|
| `url` | `uri` | Required unless `valueSet` is sent. Matches stored `ValueSet.uri`. Recognised SNOMED canonical: see [§5](#5-snomed-fhir_vs-url-family). |
| `valueSetVersion` | `string` | Stored-VS only. Selects a specific version; defaults to the latest. |
| `valueSet` | `ValueSet` resource | Inline path. Mutually exclusive with `url` (inline takes precedence when both are present). |
| `offset` | `integer` | Skip the first N concepts. `offset >= expansion.total` → empty `contains`. Honoured on all three paths. |
| `count` | `integer` | Keep at most N concepts. Honoured on all three paths. |
| `filter` | `string` | Case-insensitive substring match against `concept.code` and `concept.display`. Applied **before** paging. |
| `displayLanguage` | `code` / `string` | Preferred designation language for `expansion.contains[].display`. |
| `defaultLanguage` | `code` / `string` | Fallback when `displayLanguage` is unset. |
| `includeDesignations` | `boolean` | When `true`, emit `expansion.contains[].designation[]` from the matched concept's additional designations. |
| `activeOnly` | `boolean` | Stored-VS / inline-VS only — drops inactive concepts. SNOMED `?fhir_vs=refset/…` already returns active-only members per IG. |
| `excludeNested` | `boolean` | Stored-VS only — flatten the expansion hierarchy. |

`expansion.total` reports the **pre-paging** count; `expansion.offset` reflects the `offset` parameter when present.

## 4. Use-cases

### Stored-VS pagination

```http
POST /fhir/ValueSet/$expand
Content-Type: application/fhir+json

{ "resourceType": "Parameters", "parameter": [
  { "name": "url",    "valueUri":     "http://hl7.org/fhir/ValueSet/observation-codes" },
  { "name": "offset", "valueInteger": 100 },
  { "name": "count",  "valueInteger": 50 }
]}
```

Returns 50 concepts starting at the 100th, with `expansion.total` = the full snapshot size.

### Inline VS expansion

```http
POST /fhir/ValueSet/$expand
Content-Type: application/fhir+json

{ "resourceType": "Parameters", "parameter": [
  { "name": "valueSet", "resource": {
    "resourceType": "ValueSet",
    "compose": { "include": [
      { "system": "http://loinc.org",
        "filter": [{ "property": "STATUS", "op": "=", "value": "ACTIVE" }] }
    ]}
  }},
  { "name": "filter", "valueString": "glucose" },
  { "name": "count",  "valueInteger": 20 }
]}
```

Useful for ad-hoc queries that don't merit storing a ValueSet, and for the `tx-router`-style "evaluate this compose against your upstream" workflow.

### SNOMED typeahead

```http
GET /fhir/ValueSet/$expand?url=http://snomed.info/sct?fhir_vs=isa/404684003&filter=diabetes&count=20
```

Returns up to 20 concepts that are descendants-or-self of `404684003 |Clinical finding|` and whose code or display matches `diabetes`.

### Paginate any CodeSystem by canonical (LOINC, RxNorm, etc.)

When the client knows a stored CodeSystem's canonical but no ValueSet wraps it, address the CodeSystem directly. All four URL forms below resolve to the same implicit synthesised ValueSet and return a paginated slice of LOINC answer-list codes:

```http
GET /fhir/ValueSet/$expand?url=http://loinc.org/answer-list&count=50
GET /fhir/ValueSet/$expand?url=http://loinc.org/answer-list?fhir_vs&count=50
GET /fhir/ValueSet/$expand?url=http://loinc.org/answer-list|2.82&count=50
GET /fhir/ValueSet/$expand?url=http://loinc.org/answer-list&valueSetVersion=2.82&count=50
```

- The bare `?fhir_vs` suffix is stripped before the CodeSystem lookup (the `?fhir_vs=<pattern>` family is SNOMED-only — non-SNOMED canonicals with a valued `?fhir_vs=…` 404).
- Both the pipe-version syntax (`|2.82`) and the `valueSetVersion` parameter pin the include to a specific CodeSystem version. `valueSetVersion` wins when both are supplied.
- Combine with `filter` for typeahead UI: `&filter=glucose&count=20`.

## 5. SNOMED `?fhir_vs` URL family

Per the [HL7 UTG SNOMED CT page](https://build.fhir.org/ig/HL7/UTG/en/SNOMEDCT.html), the SNOMED canonical URL supports five implicit-ValueSet patterns. TermX recognises all five and translates the URL into a `ValueSetVersionRule` filter that `SnomedValueSetExpandProvider.composeEcl()` further translates into ECL:

| Input URL fragment | Synthesised filter | ECL emitted by `composeEcl` | Meaning |
|---|---|---|---|
| `?fhir_vs` | `operator=ecl, value="*"` | `*` | All concepts in the edition/version. |
| `?fhir_vs=isa/<sctid>` | `operator=is-a, value=<sctid>` | `<<<sctid>` | Descendants of `<sctid>` **plus the concept itself** (per IG). |
| `?fhir_vs=refset` | `operator=is-a, value=900000000000455006` | `<<900000000000455006` | All concepts under `Reference set foundation` — i.e. the catalog of refsets. |
| `?fhir_vs=refset/<sctid>` | `operator=in, value=<sctid>` | `^<sctid>` | Active members of refset `<sctid>`. |
| `?fhir_vs=ecl/<URL-encoded>` | `operator=ecl, value=<URL-decoded>` | `<expr>` | Concepts matching the ECL expression. URI-decoded before being forwarded. |

Both base canonicals are accepted:

- `http://snomed.info/sct?fhir_vs…` — unversioned. Snowstorm resolves against its default edition.
- `http://snomed.info/sct/<edition>/version/<YYYYMMDD>?fhir_vs…` — the IG-preferred versioned form. The base URI before `?` is set on `rule.codeSystemVersion.uri`; the provider's `getBranch(...)` extracts the edition id, looks it up in the `snomed-module` CodeSystem, and appends the formatted date to form a Snowstorm branch path (e.g. `MAIN/2024-01-01`).

When no `?fhir_vs` query is present (`http://snomed.info/sct` alone), the operation falls through to the stored-VS / implicit-CS lookups (§6 below).

## 6. Implicit ValueSet over a stored CodeSystem

Any `url` that doesn't match a stored ValueSet but **does** match a stored `CodeSystem.uri` is treated as the implicit ValueSet of that CodeSystem. The operation synthesises:

```json
{
  "resourceType": "ValueSet",
  "url":     "<stripped-canonical>",
  "status":  "active",
  "compose": { "include": [{
    "system":  "<stripped-canonical>",
    "version": "<resolved-version, if any>"
  }]}
}
```

and routes it through the inline-VS path (`expandFromJson`). `filter`, `offset`, `count`, and the rest of the parameter set apply unchanged.

### URL syntax tolerated

| Form | Behaviour |
|---|---|
| `http://loinc.org/answer-list` | Resolves to the CS with that URI; all concepts |
| `http://loinc.org/answer-list?fhir_vs` | Bare `?fhir_vs` is **stripped** before the CS lookup |
| `http://loinc.org/answer-list\|2.82` | Pipe-version syntax; `2.82` becomes `compose.include[].version` |
| `http://loinc.org/answer-list\|2.82?fhir_vs` | Both stripped — pipe version + bare fhir_vs |
| `http://loinc.org/answer-list` + `valueSetVersion=2.82` | `valueSetVersion` parameter wins over pipe-version when both are supplied |

Non-SNOMED canonicals with a **valued** `?fhir_vs=<pattern>` (e.g. `http://loinc.org?fhir_vs=isa/123`) are **not** interpreted — the `?fhir_vs=…` family is SNOMED-only, so these requests 404. Clients that need is-a / refset / ECL semantics should call SNOMED with the equivalent `?fhir_vs=…` URL.

## 7. Test coverage

Locked in by `terminology/src/test/groovy/org/termx/terminology/fhir/valueset/operations/ValueSetExpandOperationTest.groovy` — 20 specs.

**Stored-VS** (5 specs)

| Behaviour | Test |
|---|---|
| Without `offset`/`count` → hands full snapshot to mapper | `stored-VS expand without offset/count hands full snapshot to mapper` |
| `count=10` → 10-item slice | `stored-VS expand with count=10 paginates the snapshot to 10 items` |
| `offset=20 count=5` → 5-item slice starting at index 20 | `stored-VS expand with offset=20 count=5 returns slice starting at offset` |
| `offset=10` (no `count`) → drops first 10 | `stored-VS expand with offset=10 (no count) skips first 10 items` |
| `filter` substring → matching concepts only | `stored-VS expand with filter restricts results to matching concepts` |

**Inline-VS** (3 specs) — same pagination semantics as stored-VS, exercised through the `valueSet` parameter and the `expandFromJson` SQL path:

- `inline-VS expand without offset/count returns all concepts`
- `inline-VS expand with offset=2 count=3 returns slice starting at offset`
- `inline-VS expand with count=4 (no offset) returns first 4 concepts`

**SNOMED implicit-VS** (6 specs) — each asserts on the **captured `ValueSetVersionRule`** (operator + value) the operation hands to the mocked `ValueSetExternalExpandProvider`, so the URL→ECL contract is pinned, not just "the call doesn't 404":

- ``SNOMED ?fhir_vs (all concepts) delegates to provider with ECL `*` ``
- `SNOMED ?fhir_vs=isa/<code> delegates with is-a filter and includes the anchor`
- `SNOMED ?fhir_vs=refset delegates with is-a 900000000000455006 (Reference set foundation)`
- ``SNOMED ?fhir_vs=refset/<code> delegates with `in` filter (refset members)``
- `SNOMED ?fhir_vs=ecl/<expr> URL-decodes and passes the expression through`
- `SNOMED versioned URL passes the edition/version URI on the rule`

**Implicit VS over a stored CodeSystem** (6 specs) — each captures the JSON the operation hands to `expandFromJson` and asserts the synthesised `compose.include[].system` (+ optional `version`):

- `expand with url matching a stored CodeSystem synthesises inline VS over that CS`
- `expand with url + bare ?fhir_vs strips the suffix and resolves via CS lookup`
- `expand with parent CodeSystem canonical (http://loinc.org) ?fhir_vs synthesises VS for the parent CS`
- `expand with url containing |<version> canonical syntax extracts the version`
- `expand with url + valueSetVersion param pins the include version`
- `expand with url matching neither stored ValueSet nor stored CodeSystem returns 404`

## 8. Limitations & known gaps

- **`expansion.total` on stored-VS path** reports the post-paging slice size because the mapper at `ValueSetFhirMapper.toFhirExpansion(...)` reads `concepts.size()` rather than `snapshot.conceptsTotal`. The operation now passes the original `conceptsTotal` through on a snapshot copy, but the mapper still wins. Cosmetic — clients can fall back to `expansion.contains.length + offset` for now. Separate task.
- **SNOMED "all concepts" materialises the full result** before client-side paging. `SnomedValueSetExpandProvider.filterConcepts()` uses `SnomedConceptSearchParams.setAll(true)`, which is fine for hundreds of concepts but expensive for the international edition (~350k). The fix is to push `offset`/`count` down into `SnomedService.searchConcepts`; left as a separate optimisation.
- **`filter` on the SNOMED path is client-side**. Snowstorm's ECL doesn't carry a free-text filter parameter, so TermX applies the substring match after the provider returns. Functionally correct, less efficient than letting Snowstorm filter.
- **Non-SNOMED `?fhir_vs=<pattern>` URLs are not interpreted.** The full `?fhir_vs=isa/…`, `?fhir_vs=refset[/…]`, `?fhir_vs=ecl/…` family is SNOMED-only; the bare `?fhir_vs` suffix is leniently stripped on any canonical, but non-SNOMED canonicals with a valued `?fhir_vs=…` 404. Equivalent semantics on LOINC etc. would need a per-CodeSystem ECL-like evaluator that TermX doesn't currently embed.
- **`activeOnly` is honoured only on the stored-VS / inline-VS paths.** For SNOMED, "active" semantics depend on Snowstorm and the ECL expression — most patterns (`isa/`, `refset/`) already return active concepts by default.
