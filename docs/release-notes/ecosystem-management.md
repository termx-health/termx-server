# Ecosystem & Server Management — Release Notes

## New Feature: FHIR TX Ecosystem Management

### Summary

Added Ecosystem Management for defining FHIR TX Ecosystem configurations through the TermX web interface. Ecosystems group existing Terminology Servers and expose them via a public API in the ecosystem.json format. Server management was also significantly enhanced with a Resource Context UI and authoritative resource management.

### What's New

**Ecosystem Management (TermX Server):**
- New `sys.ecosystem` and `sys.ecosystem_server` tables (Liquibase migration `80-ecosystem.sql`)
- Internal REST API (`/ecosystems`) with CRUD operations using Space privileges
- Public API (`/public/ecosystems/{code}`) returning ecosystem.json directly in FHIR TX IG format
- Public API (`/public/ecosystems/{code}/server/{server-code}`) for individual server definitions
- Public API (`/public/ecosystems`) listing all active ecosystems
- Ecosystem references servers via many-to-many join table; ecosystem.json built dynamically from server definitions

**Ecosystem Management (TermX Web):**
- Ecosystem list page with code, name, server tags, ecosystem.json view/download links
- Ecosystem edit page with metadata form + server multiselect
- Menu entry "Ecosystems" under Management section
- Routes: `/ecosystems`, `/ecosystems/add`, `/ecosystems/:id/edit`

**Server Management Enhancements (TermX Server):**
- 10 new fields on TerminologyServer: `cachePeriodHours`, `strategy`, `authoritativeConceptmaps`, `authoritativeStructuredefinitions`, `authoritativeStructuremaps`, `open`, `token`, `oauthFlag`, `smartFlag`, `certFlag`
- `AuthoritativeResource` model with `url`, `name`, `version`, `status` fields and `toEcosystemUrl()` conversion
- Authoritative URL validation (blank check + regex syntax validation via `AuthoritativeUrlMatcher`)
- `TerminologyServerAuthoritativeService` — resolves authoritative patterns against TermX DB resources
- New endpoint `GET /servers/{id}/resources/{type}` — returns resources matching authoritative patterns
- `TerminologyServerService.convertToEcosystemServer()` — converts server to ecosystem.json format
- Import/export: `POST /servers/import/ecosystem` and `GET /servers/export/ecosystem`

**Server Management UI Refactoring (TermX Web):**
- Full rename: `TerminologyServer` → `Server` (files, classes, routes, i18n, menu, privileges)
- Route changed from `/terminology-servers` to `/servers`
- Refactored from monolithic edit form to Resource Context pattern with 3 tabs:
  - **Summary** tab: Server info widget (code, URL, kind, FHIR versions, operations, access flags) + Authoritative resources widget (CS, VS, CM, SD, SM with editable tables)
  - **Metadata** tab: Full edit form with view/edit mode toggle
  - **Resources** tab: Resolved resources matching authoritative patterns, grouped by type (CodeSystems, ValueSets, ConceptMaps, StructureDefinitions, StructureMaps)
- Uses shared `ResourceContextComponent` for consistent header/tabs (same as CodeSystem/ValueSet)

**HTX Router:**
- New `ecosystem_url` field on server entries in ecosystem.json
- `RemoteEcosystemLoader` for fetching ecosystem configs from TermX public API
- Configurable caching (`htx.ecosystem.remote.cache-ttl-hours`, default 4h)
- Stale cache fallback on fetch failure

**Shared Libraries:**
- `AuthoritativeUrlMatcher`, `GlobMatcher`, `CanonicalUrlParser` copied to `org.termx.core.util.canonical` (from htx-router) for server-side URL pattern matching

**Documentation:**
- New "Simple List and Edit Form" pattern (`docs/conventions/simple-list-edit-pattern.md`)
- New "Resource Context Pattern" (`docs/conventions/resource-context-pattern.md`) — covers both versioned (CodeSystem) and non-versioned (Server) contexts
- Updated `database-rules.md` with `jsonb_trunc` gotcha, named constraint conventions, FK index rules
- Updated `termx-web.md` with Marina-UI pitfalls table (8+ common import/binding gotchas)
- Updated `architecture-rules.md` with UI reference component rule

### Migration Notes

- Liquibase migrations create `sys.ecosystem` and `sys.ecosystem_server` tables automatically
- Existing Terminology Server data is preserved; all new columns are nullable with sensible defaults
- The 5 authoritative resource lists are stored as `jsonb` arrays on `sys.terminology_server`
- Backend API path changed from `/terminology-servers` to `/servers`
- Frontend privilege strings remain `*.TerminologyServer.edit/view` (backend unchanged)
- HTX Router: optionally add `"ecosystem_url": "https://termx.example.org/public/ecosystems/{code}"` to ecosystem.json
