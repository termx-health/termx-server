# Code System Supplements Analysis

## Scope

This note describes how code system supplements are currently used in the server, with emphasis on:

- Code system `$lookup`
- Code system `$validate-code`
- Value set expansion

The analysis is based on the current implementation in the repository as of 2026-04-04.

## Summary

The current implementation is asymmetric:

- `$lookup` has explicit runtime supplement handling.
- `$validate-code` has partial runtime supplement handling.
- Value set expansion does not do generic runtime supplement resolution.

In practice, this means lookup can actively merge supplement designations into a base concept response, while expansion only sees supplement data if that data is already attached to the resolved concept versions or is injected by an external expand provider.

## Data Model and Storage

Supplements are modeled as separate code systems with:

- `content = supplement`
- `baseCodeSystem` pointing to the base code system

Relevant code:

- [`CodeSystemSupplementService.java`](/job/helex/htx/termx-server/terminology/src/main/java/org/termx/terminology/terminology/codesystem/entity/CodeSystemSupplementService.java#L63)

When supplement entity versions are created from base entity versions, the supplement version keeps a pointer to the base entity version through `baseEntityVersionId`.

Relevant code:

- [`CodeSystemSupplementService.java`](/job/helex/htx/termx-server/terminology/src/main/java/org/termx/terminology/terminology/codesystem/entity/CodeSystemSupplementService.java#L115)

At load time, designation and property repositories can mark rows as supplement-derived when they come from the base entity version.

Relevant code:

- [`DesignationRepository.java`](/job/helex/htx/termx-server/terminology/src/main/java/org/termx/terminology/terminology/codesystem/designation/DesignationRepository.java#L52)
- [`EntityPropertyValueRepository.java`](/job/helex/htx/termx-server/terminology/src/main/java/org/termx/terminology/terminology/codesystem/entitypropertyvalue/EntityPropertyValueRepository.java#L45)

The generic merge point is entity-version decoration. If a version has `baseEntityVersionId`, the service loads both the supplement version’s own data and the base version’s data, marking the base-derived items with `supplement=true`.

Relevant code:

- [`CodeSystemEntityVersionService.java`](/job/helex/htx/termx-server/terminology/src/main/java/org/termx/terminology/terminology/codesystem/entity/CodeSystemEntityVersionService.java#L129)

## `$lookup`

`$lookup` has first-class supplement handling at request time.

The operation:

1. Resolves the base code system from `system`
2. Loads the base concept by `code`
3. Reads `displayLanguage`
4. Loads supplement designations
5. Merges those designations with the base designations
6. Picks the response `display` from the merged designation set

Relevant code:

- [`CodeSystemLookupOperation.java`](/job/helex/htx/termx-server/terminology/src/main/java/org/termx/terminology/fhir/codesystem/operations/CodeSystemLookupOperation.java#L103)

### Explicit supplement loading

If the request contains one or more `useSupplement` parameters, the operation resolves each supplement by canonical URI and loads the same code from that supplement code system.

Relevant code:

- [`CodeSystemLookupOperation.java`](/job/helex/htx/termx-server/terminology/src/main/java/org/termx/terminology/fhir/codesystem/operations/CodeSystemLookupOperation.java#L171)

### Auto-discovery by language

If `displayLanguage` is present, `$lookup` also auto-discovers supplements by querying code systems where:

- `baseCodeSystem = <base code system id>`
- `content = supplement`

That discovered supplement set is merged with any explicit `useSupplement` parameters.

Relevant code:

- [`CodeSystemLookupOperation.java`](/job/helex/htx/termx-server/terminology/src/main/java/org/termx/terminology/fhir/codesystem/operations/CodeSystemLookupOperation.java#L181)

### What is actually merged

The lookup merge is designation-only. The code appends supplement designations into the designation list used for:

- display selection
- returned `designation` parameters

Relevant code:

- [`CodeSystemLookupOperation.java`](/job/helex/htx/termx-server/terminology/src/main/java/org/termx/terminology/fhir/codesystem/operations/CodeSystemLookupOperation.java#L125)

Properties are not loaded from supplements in this path. Returned `property` parameters come from the base concept version’s property values.

Relevant code:

- [`CodeSystemLookupOperation.java`](/job/helex/htx/termx-server/terminology/src/main/java/org/termx/terminology/fhir/codesystem/operations/CodeSystemLookupOperation.java#L151)

### Tests

There is direct test coverage for:

- explicit supplement use
- auto-loading supplements by `displayLanguage`
- validating supplement-driven UCUM abbreviations and definitions

Relevant code:

- [`CodeSystemUcumOperationsTest.groovy`](/job/helex/htx/termx-server/terminology/src/test/groovy/org/termx/terminology/fhir/codesystem/operations/CodeSystemUcumOperationsTest.groovy#L40)

## `$validate-code`

`$validate-code` has related but narrower supplement support.

The operation loads the base concept, then calls `mergeSupplements(...)`, which merges in designations from supplement concepts resolved from `useSupplement`.

Relevant code:

- [`CodeSystemValidateCodeOperation.java`](/job/helex/htx/termx-server/terminology/src/main/java/org/termx/terminology/fhir/codesystem/operations/CodeSystemValidateCodeOperation.java#L151)

Important difference from `$lookup`:

- `$validate-code` does not auto-discover supplements from `displayLanguage`
- it only uses supplements explicitly listed via `useSupplement`

The merged designation set is then used to derive valid displays for comparison.

Relevant code:

- [`CodeSystemValidateCodeOperation.java`](/job/helex/htx/termx-server/terminology/src/main/java/org/termx/terminology/fhir/codesystem/operations/CodeSystemValidateCodeOperation.java#L171)

## Value Set Expansion

Value set expansion does not have an equivalent generic supplement-resolution step.

`ValueSetExpandOperation` extracts:

- `displayLanguage`
- `includeDesignations`

and passes them into `ValueSetVersionConceptService.expand(...)`.

Relevant code:

- [`ValueSetExpandOperation.java`](/job/helex/htx/termx-server/terminology/src/main/java/org/termx/terminology/fhir/valueset/operations/ValueSetExpandOperation.java#L93)

Notably, it does not:

- parse `useSupplement`
- discover supplement code systems at runtime
- load supplement concepts by canonical URI

### Where expansion gets designations

Expansion is built from `ValueSetVersionConceptService`. That service decorates expanded concepts by loading the underlying `CodeSystemEntityVersion`s and collecting their designations, properties, associations, and status.

Relevant code:

- [`ValueSetVersionConceptService.java`](/job/helex/htx/termx-server/terminology/src/main/java/org/termx/terminology/terminology/valueset/expansion/ValueSetVersionConceptService.java#L124)

For designations:

- the display is selected from the designation set
- `additionalDesignations` are populated from the same designation set
- supplement-derived designations are filtered only by the existing local rules

Relevant code:

- [`ValueSetVersionConceptService.java`](/job/helex/htx/termx-server/terminology/src/main/java/org/termx/terminology/terminology/valueset/expansion/ValueSetVersionConceptService.java#L156)

### What this means in practice

Expansion only sees supplement data if that data is already attached to the entity versions returned for the expansion result.

That happens when:

- the resolved concept version is itself a supplement-backed entity version with `baseEntityVersionId`, so entity-version decoration merges the base/supplement data
- an external expand provider returns concepts that already include `additionalDesignations`

It does not happen through a generic runtime supplement discovery mechanism equivalent to `$lookup`.

### External provider path

`ValueSetVersionConceptService` also appends results from `ValueSetExternalExpandProvider`s.

Relevant code:

- [`ValueSetVersionConceptService.java`](/job/helex/htx/termx-server/terminology/src/main/java/org/termx/terminology/terminology/valueset/expansion/ValueSetVersionConceptService.java#L105)

The unit tests show this path being used to provide UCUM-like additional designations during expansion.

Relevant code:

- [`ValueSetVersionConceptServiceTest.groovy`](/job/helex/htx/termx-server/terminology/src/test/groovy/org/termx/terminology/terminology/valueset/expansion/ValueSetVersionConceptServiceTest.groovy#L24)

This is provider-driven enrichment, not generic supplement resolution.

## Value Set FHIR Representation

The FHIR mapper can represent supplement semantics in exported ValueSets by:

- placing the base code system URI into `compose.include.system`
- adding the supplement canonical as the `valueset-supplement` extension

Relevant code:

- [`ValueSetFhirMapper.java`](/job/helex/htx/termx-server/terminology/src/main/java/org/termx/terminology/fhir/valueset/ValueSetFhirMapper.java#L214)

That affects representation/export. It does not mean the `$expand` runtime consumes the same extension into supplement loading logic.

## Current Behavioral Differences

### `$lookup`

- Supports explicit `useSupplement`
- Supports auto-discovery by `displayLanguage`
- Merges supplement designations at request time
- Does not merge supplement properties at request time

### `$validate-code`

- Supports explicit `useSupplement`
- Does not auto-discover by `displayLanguage`
- Uses merged designations when validating display text

### `$expand`

- Accepts `displayLanguage` and `includeDesignations`
- Does not resolve supplements from `useSupplement`
- Does not auto-discover supplements by base code system and language
- Only includes supplement data if it is already present on resolved entity versions or provided by an external expand provider

## Main Conclusion

Supplements are fully modeled in persistence and are actively used in lookup-style operations, but value set expansion does not currently use the same generic runtime supplement-resolution model.

The practical result is:

- lookup behavior is supplement-aware
- validate-code is partly supplement-aware
- expansion is supplement-aware only indirectly

So if the expectation is that value set expansion should behave like `$lookup` and dynamically merge applicable supplements for a base code system, that behavior is not implemented generically today.
