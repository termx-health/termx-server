# UCUM Supplement TS API Follow-up

## Context

This note continues the broader supplement analysis in [`docs/code-system-supplements-analysis.md`](/job/helex/htx/termx-server/docs/code-system-supplements-analysis.md).

The immediate goal of this follow-up was:

- remove practical dependence on the legacy measurement-unit representation for UCUM display text
- make the TS concept APIs capable of returning supplement-enriched concept data
- let `termx-web` request that enriched data for UCUM code displays, especially in code-system reference style components

The implementation work described here was done on branch `ucum-leftover-removal` on 2026-04-04.

## Findings

### 1. The branch removes the old measurement-unit server path, but the web still depends on it for UCUM display labels

The server branch already removes the dedicated measurement-unit persistence and API path.

However, `termx-web` still had a UCUM-specific fallback in [`localized-concept-name-pipe.ts`](/job/helex/htx/termx-web/app/src/app/resources/_lib/code-system/pipe/localized-concept-name-pipe.ts) that bypassed concepts entirely and called `MeasurementUnitLibService`.

That meant:

- UCUM display text in parts of the web was still sourced from the removed legacy path
- supplement-based designations could not become the single display source for UCUM until the web stopped using that fallback

### 2. The TS concept APIs were not supplement-aware

The non-FHIR concept APIs:

- `GET /ts/concepts`
- `GET /ts/concepts/{id}`
- `GET /ts/code-systems/{codeSystem}/concepts`
- `GET /ts/code-systems/{codeSystem}/concepts/{code}`
- `GET /ts/code-systems/{codeSystem}/versions/{version}/concepts/{code}`

previously returned decorated base concept versions only. They did not perform runtime supplement resolution similar to FHIR `$lookup`.

### 3. Existing supplement behavior was duplicated across FHIR operations

Before this change:

- `$lookup` had its own explicit supplement-loading logic
- `$validate-code` had a different explicit supplement-loading path
- TS concept APIs had none

That made supplement behavior harder to reason about and kept UCUM localization inconsistent across API surfaces.

## Chosen API Contract

The TS concept request model now supports these fields:

- `includeSupplement`
- `displayLanguage`
- `useSupplement`

### Intended semantics

- `includeSupplement=true`
  Enables runtime supplement merge for the concept response.
- `displayLanguage=<lang>`
  Filters supplement designations by language and enables supplement auto-discovery for the base code system when `includeSupplement=true`.
- `useSupplement=<canonical[,canonical2...]>`
  Allows explicit supplement selection, optionally with `canonical|version`.

### Why this shape

This keeps the contract close to the existing FHIR supplement model while still giving the TS APIs an explicit opt-in flag instead of changing all concept responses implicitly.

For the current UCUM web use case, the normal request is:

- `includeSupplement=true`
- `displayLanguage=<current UI language>`

That lets the server auto-discover applicable UCUM supplements and merge their designations into concept versions returned to the web.

## Implemented Server Changes

### 1. Added supplement request fields to `ConceptQueryParams`

Relevant file:

- [`ConceptQueryParams.java`](/job/helex/htx/termx-server/termx-api/src/main/java/org/termx/ts/codesystem/ConceptQueryParams.java)

### 2. Added reusable runtime supplement merge for concept responses

Relevant file:

- [`ConceptSupplementService.java`](/job/helex/htx/termx-server/terminology/src/main/java/org/termx/terminology/terminology/codesystem/concept/ConceptSupplementService.java)

Current implementation details:

- explicit supplements are resolved by canonical URI
- auto-discovered supplements are resolved by `baseCodeSystem + content=supplement`
- if `useSupplement=canonical|version` is provided, that exact supplement version is used
- otherwise one effective supplement version is selected per supplement code system: the latest active version
- supplement concepts are loaded in batches for returned codes
- supplement designations are merged into concept version designations
- merged designations are marked with `supplement=true`
- duplicate designations are deduplicated by designation type + language + value

### 3. Wired supplement merge into TS concept query/load paths

Relevant files:

- [`ConceptService.java`](/job/helex/htx/termx-server/terminology/src/main/java/org/termx/terminology/terminology/codesystem/concept/ConceptService.java)
- [`ConceptController.java`](/job/helex/htx/termx-server/terminology/src/main/java/org/termx/terminology/terminology/codesystem/concept/ConceptController.java)
- [`CodeSystemController.java`](/job/helex/htx/termx-server/terminology/src/main/java/org/termx/terminology/terminology/codesystem/CodeSystemController.java)

This means the TS API can now return supplement-enriched concept data when requested, both for search-style calls and direct concept loads.

### 4. Simplified FHIR supplement handling to use concept-query enrichment

Relevant files:

- [`CodeSystemLookupOperation.java`](/job/helex/htx/termx-server/terminology/src/main/java/org/termx/terminology/fhir/codesystem/operations/CodeSystemLookupOperation.java)
- [`CodeSystemValidateCodeOperation.java`](/job/helex/htx/termx-server/terminology/src/main/java/org/termx/terminology/fhir/codesystem/operations/CodeSystemValidateCodeOperation.java)

Effectively:

- `$lookup` now asks concept loading for supplement-enriched data instead of manually reloading supplement designations
- `$validate-code` now does the same
- `$validate-code` now also aligns more closely with lookup by allowing `displayLanguage`-driven supplement auto-discovery through the shared concept path

## Implemented Web Changes

### 1. Added a tiny UCUM supplement-request helper

Relevant file:

- [`concept-supplement-util.ts`](/job/helex/htx/termx-web/app/src/app/resources/_lib/code-system/util/concept-supplement-util.ts)

Current policy:

- only `ucum` automatically opts into supplement-aware concept loading

### 2. Moved UCUM localized display resolution onto concept APIs

Relevant file:

- [`localized-concept-name-pipe.ts`](/job/helex/htx/termx-web/app/src/app/resources/_lib/code-system/pipe/localized-concept-name-pipe.ts)

The old `MeasurementUnitLibService` UCUM special case was removed from this path. UCUM name resolution now goes through concept search with supplement params.

### 3. Updated code-system coding reference loading

Relevant file:

- [`code-system-coding-reference.service.ts`](/job/helex/htx/termx-web/app/src/app/resources/code-system/services/code-system-coding-reference.service.ts)

This is the key path for code-system reference style displays. When the referenced coding belongs to UCUM, the web now requests supplement-enriched concept data and uses that for display derivation.

### 4. Updated concept search surfaces used for code selection

Relevant files:

- [`concept-search.component.ts`](/job/helex/htx/termx-web/app/src/app/resources/_lib/code-system/containers/concept-search.component.ts)
- [`concept-drawer-search.component.ts`](/job/helex/htx/termx-web/app/src/app/resources/_lib/code-system/containers/concept-drawer-search.component.ts)

This improves consistency when searching or selecting UCUM concepts in the web UI.

## Remaining Gaps

### 1. Runtime supplement merge is still designation-focused

The new TS concept enrichment currently merges supplement designations only.

It does **not** yet perform generic runtime merging of:

- supplement property values
- supplement associations

That matches the current practical need for UCUM display localization, but it is not a full runtime supplement materialization model.

### 2. Accepted Risk: direct concept GET endpoints do not currently propagate supplement-view permissions

The supplement decoration path relies on `ConceptQueryParams.permittedCodeSystems` when it loads supplement concepts.

For concept query/search endpoints this value is populated normally.

For direct concept GET endpoints:

- `GET /ts/concepts/{id}`
- `GET /ts/code-systems/{codeSystem}/concepts/{code}`
- `GET /ts/code-systems/{codeSystem}/versions/{version}/concepts/{code}`

the current implementation decorates an already-loaded base concept and does not explicitly inject the caller's permitted code systems into the supplement-loading subquery.

Practical consequence:

- if supplement decoration is requested on those GET endpoints, supplement designations may be loaded without the same code-system visibility filtering that applies to normal concept queries

This is currently treated as an accepted risk for the branch because:

- the immediate target is the UCUM supplement path
- UCUM supplement access is expected to follow the UCUM base code-system access model in the intended deployment
- the clean fix should be done together with the broader refinement of version-specific supplement resolution

Recommended future fix:

- pass `SessionStore.require().getPermittedResourceIds(Privilege.CS_VIEW)` into the `ConceptQueryParams` used by direct concept GET supplement decoration

### 3. Legacy measurement-unit UI code still exists in `termx-web`

The web still contains the older measurement-unit module and services in multiple files under [`app/src/app/measurement-unit`](/job/helex/htx/termx-web/app/src/app/measurement-unit) and related imports.

This pass only removed the UCUM display dependency from the main concept-display path. It did **not** complete a full deletion or migration of all legacy measurement-unit UI functionality.

### 4. Verification is partially blocked by private dependency access

Attempted verification in `termx-server`:

- `./gradlew :termx-api:compileJava :terminology:compileJava`
- `./gradlew :terminology:test --tests '*CodeSystemUcumOperationsTest'`

Both failed before compilation/test execution because the build could not fetch `com.kodality.kefhir:kefhir-core:R5.5.1` from GitHub Packages and received HTTP `401 Unauthorized`.

So:

- the code was reviewed locally
- the web build artifacts were regenerated under `termx-web/dist/app`
- server-side compile/test verification remains blocked by repository credentials, not by a confirmed compile failure from these changes

## Recommended Next Follow-up

If the branch continues:

1. Decide whether TS concept enrichment should also merge supplement properties.
2. Fix direct concept GET supplement permission filtering.
3. Decide whether the remaining measurement-unit web module should be fully removed or explicitly preserved as a separate legacy UI.
4. Re-run the server compile/tests in an environment with valid GitHub Packages credentials.
