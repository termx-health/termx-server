# UCUM Supplement TS API Follow-up

## Scope

This note records the supplement-related work that has already been implemented around UCUM TS concept APIs and the remaining constraints that still matter.

It continues the broader supplement overview in [`docs/code-system-supplements-analysis.md`](/job/helex/htx/termx-server/docs/code-system-supplements-analysis.md).

## Implemented Outcome

The old measurement-unit-specific UCUM display path is no longer the primary source for web-facing UCUM labels.

Instead:

- TS concept APIs can return supplement-enriched UCUM concept data
- FHIR `$lookup` and `$validate-code` use the same shared supplement enrichment path
- `termx-web` requests supplement-aware concept data for UCUM display resolution and search
- UCUM TS search can match supplement designations and supplement-only valid UCUM expressions

## Server Changes

### TS concept supplement enrichment

`ConceptQueryParams` now supports:

- `includeSupplement`
- `displayLanguage`
- `useSupplement`

Relevant file:

- [`ConceptQueryParams.java`](/job/helex/htx/termx-server/termx-api/src/main/java/org/termx/ts/codesystem/ConceptQueryParams.java)

Runtime supplement enrichment is centralized in:

- [`ConceptSupplementService.java`](/job/helex/htx/termx-server/terminology/src/main/java/org/termx/terminology/terminology/codesystem/concept/ConceptSupplementService.java)

Current behavior:

- explicit supplements are resolved by canonical URI
- auto-discovered supplements are resolved by `baseCodeSystem + content=supplement`
- `useSupplement=canonical|version` selects that exact supplement version
- otherwise one effective supplement version is selected per supplement code system: latest active version
- supplement designations are merged into concept versions and marked with `supplement=true`

The enrichment is used by:

- [`ConceptService.java`](/job/helex/htx/termx-server/terminology/src/main/java/org/termx/terminology/terminology/codesystem/concept/ConceptService.java)
- [`ConceptController.java`](/job/helex/htx/termx-server/terminology/src/main/java/org/termx/terminology/terminology/codesystem/concept/ConceptController.java)
- [`CodeSystemController.java`](/job/helex/htx/termx-server/terminology/src/main/java/org/termx/terminology/terminology/codesystem/CodeSystemController.java)

### FHIR alignment

FHIR operations now rely on the same concept enrichment model instead of duplicating supplement-loading logic:

- [`CodeSystemLookupOperation.java`](/job/helex/htx/termx-server/terminology/src/main/java/org/termx/terminology/fhir/codesystem/operations/CodeSystemLookupOperation.java)
- [`CodeSystemValidateCodeOperation.java`](/job/helex/htx/termx-server/terminology/src/main/java/org/termx/terminology/fhir/codesystem/operations/CodeSystemValidateCodeOperation.java)

This means:

- `$lookup` remains supplement-aware
- `$validate-code` now follows the same supplement enrichment path
- `displayLanguage`-driven supplement auto-discovery is aligned more closely between the two operations

### UCUM TS search and resolver behavior

UCUM-specific supplement-aware concept search is implemented in:

- [`UcumConceptResolver.java`](/job/helex/htx/termx-server/ucum/src/main/java/org/termx/ucum/ts/UcumConceptResolver.java)
- [`UcumSupplementDesignationService.java`](/job/helex/htx/termx-server/ucum/src/main/java/org/termx/ucum/ts/UcumSupplementDesignationService.java)

Current behavior:

- text and designation matching can use supplement designations
- supplement-only valid UCUM expressions can become searchable candidates
- `findByCode(...)` validates the code through UCUM validation instead of limiting itself to loaded base and defined units
- the global UCUM unit cache includes active supplement-derived units
- request-specific supplement units are checked dynamically during search instead of being stored in request-specific caches

### UCUM cache invalidation

UCUM supplement-related search caches are invalidated through:

- [`UcumSearchCacheInvalidator.java`](/job/helex/htx/termx-server/termx-core/src/main/java/org/termx/core/ts/UcumSearchCacheInvalidator.java)
- [`UcumSearchCacheInvalidatorImpl.java`](/job/helex/htx/termx-server/ucum/src/main/java/org/termx/ucum/ts/UcumSearchCacheInvalidatorImpl.java)

The resolver cache:

- can be invalidated explicitly
- is also bounded by TTL
- is invalidated after UCUM-related supplement, entity-version, version, and UCUM essence reload changes

Relevant files:

- [`CodeSystemSupplementService.java`](/job/helex/htx/termx-server/terminology/src/main/java/org/termx/terminology/terminology/codesystem/entity/CodeSystemSupplementService.java)
- [`CodeSystemEntityVersionService.java`](/job/helex/htx/termx-server/terminology/src/main/java/org/termx/terminology/terminology/codesystem/entity/CodeSystemEntityVersionService.java)
- [`CodeSystemVersionService.java`](/job/helex/htx/termx-server/terminology/src/main/java/org/termx/terminology/terminology/codesystem/version/CodeSystemVersionService.java)
- [`UcumServiceImpl.java`](/job/helex/htx/termx-server/ucum/src/main/java/org/termx/ucum/service/UcumServiceImpl.java)

## Web Changes

UCUM supplement-aware concept loading is now used in the main display/search paths:

- [`concept-supplement-util.ts`](/job/helex/htx/termx-web/app/src/app/resources/_lib/code-system/util/concept-supplement-util.ts)
- [`localized-concept-name-pipe.ts`](/job/helex/htx/termx-web/app/src/app/resources/_lib/code-system/pipe/localized-concept-name-pipe.ts)
- [`code-system-coding-reference.service.ts`](/job/helex/htx/termx-web/app/src/app/resources/code-system/services/code-system-coding-reference.service.ts)
- [`concept-search.component.ts`](/job/helex/htx/termx-web/app/src/app/resources/_lib/code-system/containers/concept-search.component.ts)
- [`concept-drawer-search.component.ts`](/job/helex/htx/termx-web/app/src/app/resources/_lib/code-system/containers/concept-drawer-search.component.ts)

Current policy:

- only `ucum` automatically opts into supplement-aware concept loading on the web side

## Remaining Relevant Gaps

### Supplement enrichment is still designation-focused

The runtime supplement merge currently focuses on designations.

It does not generically merge:

- supplement property values
- supplement associations

That matches the current UCUM localization use case, but it is not a full runtime supplement materialization model.

### Value set expansion is still not generic supplement resolution

Value set expansion still does not implement the same generic runtime supplement-resolution model used by concept lookup/query.

UCUM-related expansion enrichment remains provider-driven or entity-version-driven rather than a fully generic supplement discovery path.
