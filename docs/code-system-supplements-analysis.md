# Code System Supplements Analysis

## Scope

This note describes the current supplement behavior in `termx-server`, focusing on:

- FHIR `$lookup`
- FHIR `$validate-code`
- TS concept query/load APIs
- value set expansion

## Summary

Supplement support is now split into two clear layers:

- concept lookup/query paths have generic runtime supplement enrichment for designations
- value set expansion still does not have the same generic runtime supplement-resolution model

So the main asymmetry is no longer between lookup and TS APIs. It is now between concept-oriented APIs and expansion.

## Data Model and Storage

Supplements are stored as separate code systems with:

- `content = supplement`
- `baseCodeSystem` pointing to the base code system

Relevant file:

- [`CodeSystemSupplementService.java`](/job/helex/htx/termx-server/terminology/src/main/java/org/termx/terminology/terminology/codesystem/entity/CodeSystemSupplementService.java)

When supplement entity versions are created from base entity versions, the supplement entity version keeps a pointer to the base entity version through `baseEntityVersionId`.

Relevant file:

- [`CodeSystemSupplementService.java`](/job/helex/htx/termx-server/terminology/src/main/java/org/termx/terminology/terminology/codesystem/entity/CodeSystemSupplementService.java)

At entity-version decoration time, base and supplement data can be merged, with base-derived items marked using `supplement=true`.

Relevant file:

- [`CodeSystemEntityVersionService.java`](/job/helex/htx/termx-server/terminology/src/main/java/org/termx/terminology/terminology/codesystem/entity/CodeSystemEntityVersionService.java)

## Generic Runtime Supplement Enrichment

Runtime supplement enrichment for concept responses is centralized in:

- [`ConceptSupplementService.java`](/job/helex/htx/termx-server/terminology/src/main/java/org/termx/terminology/terminology/codesystem/concept/ConceptSupplementService.java)

The request model is carried through:

- [`ConceptQueryParams.java`](/job/helex/htx/termx-server/termx-api/src/main/java/org/termx/ts/codesystem/ConceptQueryParams.java)

Current semantics:

- `includeSupplement=true` enables runtime supplement merge
- `displayLanguage` filters supplement designations and enables supplement auto-discovery
- `useSupplement` allows explicit supplement selection, optionally with `canonical|version`
- explicit supplement versions are respected when provided
- otherwise one effective version is selected per supplement code system: latest active version

The merge is currently designation-focused:

- supplement designations are appended into concept versions
- merged designations are marked with `supplement=true`
- duplicates are removed

## `$lookup`

`$lookup` is supplement-aware through the shared concept enrichment path.

Relevant file:

- [`CodeSystemLookupOperation.java`](/job/helex/htx/termx-server/terminology/src/main/java/org/termx/terminology/fhir/codesystem/operations/CodeSystemLookupOperation.java)

Current behavior:

- supports explicit `useSupplement`
- supports supplement auto-discovery by `displayLanguage`
- uses merged designations for response display and returned `designation` parameters
- does not generically merge supplement properties at request time

## `$validate-code`

`$validate-code` now uses the same concept enrichment model rather than a separate supplement-loading implementation.

Relevant file:

- [`CodeSystemValidateCodeOperation.java`](/job/helex/htx/termx-server/terminology/src/main/java/org/termx/terminology/fhir/codesystem/operations/CodeSystemValidateCodeOperation.java)

Current behavior:

- supports explicit `useSupplement`
- follows the shared concept enrichment path
- uses the merged designation set when validating display text
- is aligned more closely with `$lookup`, including `displayLanguage`-driven supplement behavior through the shared concept path

## TS Concept APIs

TS concept query/load endpoints are also supplement-aware through the same shared enrichment path.

Relevant files:

- [`ConceptService.java`](/job/helex/htx/termx-server/terminology/src/main/java/org/termx/terminology/terminology/codesystem/concept/ConceptService.java)
- [`ConceptController.java`](/job/helex/htx/termx-server/terminology/src/main/java/org/termx/terminology/terminology/codesystem/concept/ConceptController.java)
- [`CodeSystemController.java`](/job/helex/htx/termx-server/terminology/src/main/java/org/termx/terminology/terminology/codesystem/CodeSystemController.java)

This is the main change from the earlier supplement state: supplement-aware runtime concept loading is no longer limited to FHIR operations.

## Value Set Expansion

Value set expansion still does not implement the same generic runtime supplement-resolution model used by concept lookup/query.

Relevant files:

- [`ValueSetExpandOperation.java`](/job/helex/htx/termx-server/terminology/src/main/java/org/termx/terminology/fhir/valueset/operations/ValueSetExpandOperation.java)
- [`ValueSetVersionConceptService.java`](/job/helex/htx/termx-server/terminology/src/main/java/org/termx/terminology/terminology/valueset/expansion/ValueSetVersionConceptService.java)

Current behavior:

- expansion accepts `displayLanguage` and `includeDesignations`
- expansion does not generically resolve supplements from `useSupplement`
- expansion does not implement generic supplement auto-discovery equivalent to concept lookup/query
- supplement data appears only when it is already present on resolved entity versions or is added by an external/provider-specific expansion path

For UCUM specifically, there is additional provider-driven enrichment around supplement designations, but that is still not the same as generic supplement resolution for all code systems.

## FHIR ValueSet Representation

The FHIR mapper can represent supplement semantics in exported ValueSets by:

- placing the base code system URI into `compose.include.system`
- adding the supplement canonical as the `valueset-supplement` extension

Relevant file:

- [`ValueSetFhirMapper.java`](/job/helex/htx/termx-server/terminology/src/main/java/org/termx/terminology/fhir/valueset/ValueSetFhirMapper.java)

That affects representation and export. It does not mean `$expand` consumes the same extension into generic runtime supplement loading.

## Main Conclusion

The current supplement model is:

- generic runtime supplement enrichment for concept-oriented APIs
- designation-focused merge semantics
- no generic supplement-resolution model for value set expansion

So the remaining supplement gap is now primarily expansion behavior, not concept lookup/query behavior.
