# FHIR `ValueSet/$expand`

**Feature:** Paged, filtered `$expand` over stored ValueSets, inline ValueSets posted in the request body, **implicit ValueSets over any stored CodeSystem** (delegated to `ConceptService.query`), and SNOMED CT implicit-ValueSet URLs (`?fhir_vs[=…]`).
**Spans:** [`terminology`](../../terminology) module (`ValueSetExpandOperation`) + delegation to [`snomed`](../../snomed) module (`SnomedValueSetExpandProvider` → Snowstorm).
**Introduced:** [termx-server#145](https://github.com/termx-health/termx-server/pull/145) — paging/filter fix on stored-VS path, SNOMED implicit-VS URL routing. Extended in [#146](https://github.com/termx-health/termx-server/pull/146) — CodeSystem-canonical resolution via inline VS synthesis. Refined in [#147](https://github.com/termx-health/termx-server/pull/147) — `expansion.total` correctness on all paths; implicit-CS path now routes through `ConceptService.query` instead of the SQL function so displays surface correctly and pagination happens at the DB level.

---

## 1. Description

`POST /fhir/ValueSet/$expand` (and the instance-level `POST /fhir/ValueSet/{id}/$expand`) returns a `ValueSet` resource whose `expansion.contains` lists the codes selected by the value set. TermX accepts four forms of input, resolved in this priority order:

| # | Path | How the client identifies the VS | Where the concepts come from |
|---|---|---|---|
| 1 | **Inline-VS** | `valueSet` parameter carries a `ValueSet` resource constructed by the client | `ValueSetVersionConceptRepository.expandFromJson(...)` — Postgres function `value_set_expand_jsonb` resolves `compose.include[].system + filter + concept` directly |
| 2 | **SNOMED implicit-VS** | `url` is `http://snomed.info/sct[/<edition>/version/<date>]?fhir_vs[=…]` | `SnomedValueSetExpandProvider.ruleExpand(...)` — calls Snowstorm via `SnomedService.searchConcepts(ecl=…, branch=…)` |
| 3 | **Stored-VS** | `url` matches a `ValueSet.uri` already stored in TermX, or the operation runs at instance level (`/ValueSet/{id}/$expand`) | `ValueSetVersionConceptService.expand(...)` — snapshot persisted by TermX (re-uses cache when current) |
| 4 | **Implicit VS over a stored CodeSystem** | `url` matches a `CodeSystem.uri`; no matching stored VS exists | Delegates to `ConceptService.query(codeSystem=…, codeSystemVersion=…, textContains=filter, limit=count, offset=offset, displayLanguage=…)` — same path the UI's concept-list endpoint uses |

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
| `url` | `uri` | Required unless `valueSet` is sent. Resolved in order: stored `ValueSet.uri` → SNOMED canonical (see [§5](#5-snomed-fhir_vs-url-family)) → stored `CodeSystem.uri` (see [§6](#6-implicit-valueset-over-a-stored-codesystem)). |
| `valueSetVersion` | `string` | Selects a specific version. On stored-VS: picks `ValueSet.version`. On implicit-CS: forwarded as `codeSystemVersion`. Pipe-version syntax in `url` (`<canonical>\|<version>`) is also accepted on implicit-CS; `valueSetVersion` wins when both are supplied. |
| `valueSet` | `ValueSet` resource | Inline path. Mutually exclusive with `url` (inline takes precedence when both are present). |
| `offset` | `integer` | Skip the first N concepts. `offset >= expansion.total` → empty `contains`. Honoured on all four paths. Pushed down to the DB on the implicit-CS path (`LIMIT/OFFSET` on the concept query). |
| `count` | `integer` | Keep at most N concepts. Honoured on all four paths. Pushed down to the DB on the implicit-CS path. |
| `filter` | `string` | Case-insensitive substring match against `concept.code` and `concept.display`. Applied **before** paging. On the implicit-CS path it maps to `ConceptQueryParams.textContains` (DB-side); on stored-VS, inline-VS, and SNOMED it's applied client-side after the source returns. |
| `displayLanguage` | `code` / `string` | Preferred designation language for `expansion.contains[].display`. On the implicit-CS path it's threaded into the concept query so the right designation comes back; on other paths the request-time language wins over the version-time language. |
| `defaultLanguage` | `code` / `string` | Fallback when `displayLanguage` is unset. |
| `includeDesignations` | `boolean` | When `true`, emit `expansion.contains[].designation[]` from the matched concept's additional designations. |
| `activeOnly` | `boolean` | Stored-VS / inline-VS only — drops inactive concepts. On the implicit-CS path the concept query returns active concepts by default. On SNOMED, `?fhir_vs=refset/…` already returns active-only members per IG. |
| `excludeNested` | `boolean` | Stored-VS only — flatten the expansion hierarchy. |

`expansion.total` reports the **post-filter, pre-pagination** count (FHIR R5: *"if the number of codes in an expansion is changed by the parameters supplied, then this should be the count of codes corresponding to the parameters"* — filters change the set, `offset`/`count` only window it). `expansion.offset` reflects the `offset` parameter when present.

### Expansion envelope: what clients can rely on

A response from any of the four paths has these guarantees (pinned by `ValueSetExpandOperationTest`):

| Field | Value |
|---|---|
| `expansion.total` | Post-filter, pre-pagination count. Stable across `offset`/`count` sweeps so clients can paginate without re-issuing a `count=0` discovery probe. |
| `expansion.offset` | The `offset` parameter when supplied, otherwise absent. |
| `expansion.contains[].system` | The CodeSystem URI the concept belongs to. |
| `expansion.contains[].code` | The concept code. Always set. |
| `expansion.contains[].display` | The concept's display in `displayLanguage` (or the default language) when available. Empty / absent when the source CodeSystem doesn't carry a display in any language — see [§8](#8-limitations--known-gaps) for the one path where this can happen unexpectedly. |
| `expansion.timestamp` | UTC time the expansion was produced. |

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

Returns 50 concepts starting at the 100th, with `expansion.total` = the full post-filter snapshot size (not 50 — that's the slice).

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

When the client knows a stored CodeSystem's canonical but no ValueSet wraps it, address the CodeSystem directly. All four URL forms below resolve through `ConceptService.query` against the matching CodeSystem and return a paginated slice of LOINC answer-list codes — with `display` populated from the concept's designations and `expansion.total` reporting the full set size:

```http
GET /fhir/ValueSet/$expand?url=http://loinc.org/answer-list&count=50
GET /fhir/ValueSet/$expand?url=http://loinc.org/answer-list?fhir_vs&count=50
GET /fhir/ValueSet/$expand?url=http://loinc.org/answer-list|2.82&count=50
GET /fhir/ValueSet/$expand?url=http://loinc.org/answer-list&valueSetVersion=2.82&count=50
```

- The bare `?fhir_vs` suffix is stripped before the CodeSystem lookup (the `?fhir_vs=<pattern>` family is SNOMED-only — non-SNOMED canonicals with a valued `?fhir_vs=…` 404).
- Both the pipe-version syntax (`|2.82`) and the `valueSetVersion` parameter pin the include to a specific CodeSystem version. `valueSetVersion` wins when both are supplied.
- Combine with `filter` for typeahead UI: `&filter=glucose&count=20`. The filter is pushed down to the DB and matches both concept code and display.
- Pagination is DB-level — `count=20` against a 72k-concept CodeSystem fetches 20 rows, not 72k-then-slice.

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

Any `url` that doesn't match a stored ValueSet but **does** match a stored `CodeSystem.uri` is treated as the implicit ValueSet of that CodeSystem. After URL cleaning (see syntax table below), the operation delegates to `ConceptService.query(...)` — the same internal API the UI's concept-list endpoint uses — with the following parameter mapping:

| `$expand` parameter | `ConceptQueryParams` field |
|---|---|
| `url` (canonical) | `codeSystem` (id of the resolved `CodeSystem.uri`) |
| `valueSetVersion` / pipe-version | `codeSystemVersion` |
| `filter` | `textContains` (matches concept code **and** display) |
| `displayLanguage` / `defaultLanguage` | `displayLanguage` |
| `count` | `limit` |
| `offset` | `offset` |

The response is built from the `QueryResult<Concept>`:

- `expansion.total` ← `result.meta.total` (pre-paging count from the DB)
- `expansion.contains[].code` ← `concept.code`
- `expansion.contains[].display` ← `ConceptUtil.getDisplay(concept.versions[0].designations, displayLanguage, [])` — same helper `CodeSystemLookupOperation` uses

**Why this route and not the SQL function?** The `value_set_expand_jsonb` SQL function is built for arbitrary compose rules (filters, exclusions, recursion). For the implicit-CS case the synthesised compose has only `{system, version}` — no filter complexity — and the SQL function's `cs` CTE doesn't fetch designations for system-only includes (LOINC bug observed on dev.termx.org). `ConceptService.query` fetches designations by default, pushes `limit`/`offset` into the DB, and reports the pre-paging total — three properties this path needs and the SQL function doesn't provide for system-only includes.

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

Locked in by `terminology/src/test/groovy/org/termx/terminology/fhir/valueset/operations/ValueSetExpandOperationTest.groovy` — 25 specs.

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

**`expansion.total` correctness** (3 specs) — FHIR R5: total reflects the post-filter, pre-pagination count.

- `inline-VS expand: expansion.total is the full pre-paging count`
- `inline-VS expand: total reflects the post-filter pre-paging count`
- `implicit-CS expand: expansion.total is the full pre-paging count from ConceptService`

**`expansion.contains[].display` propagation** (2 specs)

- `inline-VS expand: contains[].display is populated from concept's display field`
- `implicit-CS expand: contains[].display is picked from the concept's designations`

**SNOMED implicit-VS** (6 specs) — each asserts on the **captured `ValueSetVersionRule`** (operator + value) the operation hands to the mocked `ValueSetExternalExpandProvider`, so the URL→ECL contract is pinned, not just "the call doesn't 404":

- ``SNOMED ?fhir_vs (all concepts) delegates to provider with ECL `*` ``
- `SNOMED ?fhir_vs=isa/<code> delegates with is-a filter and includes the anchor`
- `SNOMED ?fhir_vs=refset delegates with is-a 900000000000455006 (Reference set foundation)`
- ``SNOMED ?fhir_vs=refset/<code> delegates with `in` filter (refset members)``
- `SNOMED ?fhir_vs=ecl/<expr> URL-decodes and passes the expression through`
- `SNOMED versioned URL passes the edition/version URI on the rule`

**Implicit VS over a stored CodeSystem** (6 specs) — each captures the `ConceptQueryParams` the operation hands to the mocked `ConceptService.query`, so the URL → query-param translation (`codeSystem`, `codeSystemVersion`, `limit`, `offset`) is pinned:

- `expand with url matching a stored CodeSystem delegates to ConceptService.query`
- `expand with url + bare ?fhir_vs strips the suffix and resolves via CS lookup`
- `expand with parent CodeSystem canonical (http://loinc.org) ?fhir_vs delegates against that CS`
- `expand with url containing |<version> canonical syntax extracts the version`
- `expand with url + valueSetVersion param pins the include version`
- `expand with url matching neither stored ValueSet nor stored CodeSystem returns 404`

## 8. Limitations & known gaps

- **SNOMED "all concepts" materialises the full result** before client-side paging. `SnomedValueSetExpandProvider.filterConcepts()` uses `SnomedConceptSearchParams.setAll(true)`, which is fine for hundreds of concepts but expensive for the international edition (~350k). The fix is to push `offset`/`count` down into `SnomedService.searchConcepts`; left as a separate optimisation.
- **`filter` on the SNOMED path is client-side**. Snowstorm's ECL doesn't carry a free-text filter parameter, so TermX applies the substring match after the provider returns. Functionally correct, less efficient than letting Snowstorm filter.
- **Non-SNOMED `?fhir_vs=<pattern>` URLs are not interpreted.** The full `?fhir_vs=isa/…`, `?fhir_vs=refset[/…]`, `?fhir_vs=ecl/…` family is SNOMED-only; the bare `?fhir_vs` suffix is leniently stripped on any canonical, but non-SNOMED canonicals with a valued `?fhir_vs=…` 404. Equivalent semantics on LOINC etc. would need a per-CodeSystem ECL-like evaluator that TermX doesn't currently embed.
- **`activeOnly` is honoured only on the stored-VS / inline-VS paths.** For SNOMED, "active" semantics depend on Snowstorm and the ECL expression — most patterns (`isa/`, `refset/`) already return active concepts by default.
- **Latent: SQL function `value_set_expand_jsonb` drops `display` on system-only includes.** A client posting an inline ValueSet whose `compose.include[]` has only `{system, version}` (no `concept[]`) and going through the inline-VS path will get codes back without displays. The implicit-CS path no longer goes through this function (it routes through `ConceptService.query` instead) so the user-visible bug is fixed for that case. Clients hitting the inline-VS path with system-only includes should either include explicit `concept[]` entries or fetch displays out-of-band; a SQL-level fix is tracked separately.
