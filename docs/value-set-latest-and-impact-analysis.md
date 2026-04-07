# Value Set Dynamic Code System Versioning And Impact Analysis

## Goal

Support value set rules that reference a code system without pinning a version, treat that as `latest`, keep active expansion snapshots fresh when the latest source version changes, and expose enough metadata to highlight affected value sets from the code system side.

## Final Outcome

### Rule semantics

- The persisted rule model already allows `value_set_version_rule.code_system_version_id` to be `null`.
- `null` `codeSystemVersion` is treated as `Latest`.
- An explicitly selected code system version stays explicit and is not rewritten to `Latest`.
- SQL-backed expansion and Java-backed preview now use aligned version-resolution rules for omitted versions.

### Snapshot behavior

- `ValueSetSnapshot` now stores dependency metadata describing which concrete code system version was used for each rule during snapshot generation.
- Active value set snapshots are reused only when dynamic-rule dependencies still match the current latest source versions.
- Explicitly versioned rules are expanded against the exact selected code system version.

### Activation behavior

- Activating a code system version refreshes affected dynamic value set snapshots.
- This refresh is triggered from `CodeSystemVersionService.activate(...)`, so it also applies to non-controller activation flows such as release/import/task-driven paths.

### Code system impact visibility

- `GET /ts/code-systems/{codeSystem}/value-set-impacts` returns one row per impacted value set version.
- The response distinguishes dynamic versus static references and reports:
  - `artifactId`
  - `artifactVersion`
  - `dynamic`
  - `affected`
  - `reason`
  - `snapshotCreatedAt`
  - `resolvedCodeSystemVersion`
  - `currentCodeSystemVersion`

## Approach

### 1. Represent `latest` as omitted `codeSystemVersion`

This matches the actual rule semantics without inventing a pseudo-version value in storage or payloads.

### 2. Store resolved source versions in the snapshot

This makes the snapshot self-describing and allows freshness checks and impact reporting without recomputing everything on every read.

### 3. Reuse active snapshots only when dynamic dependencies are current

Dynamic snapshots are reused only if the stored resolved dependencies still match the current latest code system versions.

### 4. Refresh dependent dynamic snapshots when a code system version is activated

This keeps cached expansions warm and avoids waiting for a later read to discover that a dynamic dependency changed.

### 5. Expose version-aware impact rows from the code system side

The generic related-artifact view was not enough because it did not distinguish which value set version was affected or why.

## Why This Is Better Than Alternatives

### Avoid fake special version codes

Using a literal pseudo-version like `latest` in persisted rules would leak UI semantics into storage and complicate FHIR/JSON interop.

### Avoid recomputing everything on every request

Re-expanding every active value set whenever it is opened would be correct but too expensive.

### Avoid using only generic related artifacts

The existing related-artifact model is too shallow for version-aware warnings and compare actions.

## Implemented Server Changes

### Snapshot dependency tracking

- `ValueSetSnapshot.dependencies` added
- `ValueSetSnapshotDependency` added
- snapshot repository save/load updated
- Liquibase changelog adds `terminology.value_set_snapshot.dependencies jsonb`

### Dynamic version resolution

- `ValueSetCodeSystemVersionResolver` added
- centralizes:
  - explicit version resolution
  - latest version resolution
  - dependency collection

### Snapshot freshness checks

- `ValueSetVersionConceptService` now:
  - stores snapshot dependencies when generating snapshots
  - reuses active snapshots only when dynamic dependencies still match the current latest source versions

### Automatic refresh on activation

- `CodeSystemVersionService.activate(...)` now triggers refresh of dynamic value set snapshots for that code system
- the refresh dependency is injected lazily to avoid a bean cycle with expansion providers

### Impact endpoint

- `CodeSystemArtifactImpact` added
- `ValueSetCodeSystemImpactService` added
- `CodeSystemController` exposes `/value-set-impacts`
- impact rows are aggregated per value set version, not per matching rule

### Expansion correctness fixes

- SQL expansion for omitted code system version now resolves the latest `active` or `draft` version consistently with the Java resolver
- SQL expansion for explicit code system version with `all concepts` now uses exact membership of the selected version instead of falling back to older versions

## Implemented Frontend Changes

`termx-web` has been updated accordingly.

Implemented UI behavior:

- value set rule form:
  - code system version selection becomes optional
  - missing version is displayed as `Latest`
- reopening a saved rule with no version keeps it empty instead of auto-selecting a version
- code system summary:
  - includes version-aware value set impact rows
  - shows warning or success state and explanatory reason text
- translations:
  - add `Latest` wording and impact texts

## Recommended Next Steps

### Backend

1. Add a validation endpoint for one value set version against a selected target code system version.
2. Reuse the existing compare model, but extend it beyond only `added/deleted` if changed concept payloads matter.
3. Consider moving activation-triggered refresh to an explicit background job if activation latency becomes noticeable.

### Frontend

1. Add a `Validate` action per impacted value set version in the code system summary.
2. Show compare results in the value set version summary.

## Notes And Limitations

- The current compare model still works at concept-code level and does not yet report changed designations/properties separately.
- Snapshots created before dependency tracking will initially appear as needing attention for dynamic rules until they are regenerated once.
- This implementation only covers code systems managed by TermX, which matches the task scope.
