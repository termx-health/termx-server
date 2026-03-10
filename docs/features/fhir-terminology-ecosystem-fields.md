# FHIR Terminology Ecosystem -- Server Fields

## Overview

TermX supports the [FHIR Terminology Ecosystem IG](https://build.fhir.org/ig/HL7/fhir-tx-ecosystem-ig/ecosystem.html) server registry format. Each terminology server in TermX can be enriched with ecosystem-specific metadata, exported to the standard FHIR ecosystem JSON format, or imported from an existing ecosystem file.

## New Server Fields

In addition to the existing fields (code, name, rootUrl, kind, headers, authConfig, active, currentInstallation), the following FHIR ecosystem fields are available:

### accessInfo
Human-readable markdown text explaining how to get access to the server.

### usage
List of intended usage tags. Standard values:
- `code-generation`
- `validation`
- `publication`

### supportedOperations
List of FHIR terminology operations the server supports:
- `$expand`, `$validate-code`, `$lookup`, `$translate`, `$subsumes`, `$closure`

### fhirVersions
List of FHIR version endpoints. Each entry has:
- `version` -- FHIR version (R3, R4, R4B, R5, R6)
- `url` -- the actual FHIR endpoint URL for that version

### authoritative
List of CodeSystems the server is authoritative for. Each entry is an enriched object:
- `url` -- canonical URL pattern (wildcards supported, e.g. `http://snomed.info/sct/*`)
- `status` -- resource status: `draft` or `active` (optional)
- `version` -- version or version pattern (optional)
- `name` -- human-readable name (optional)

### authoritativeValuesets
Same structure as `authoritative`, but for ValueSets.

### exclusions
List of CodeSystem/ValueSet canonical URL patterns the server explicitly does not serve.

## Export

`GET /api/terminology-servers/export/ecosystem?download=true`

Generates a FHIR ecosystem registry JSON file. The enriched authoritative entries are flattened to spec-compliant URL strings:
- `{url: "http://loinc.org", version: "2.77"}` becomes `"http://loinc.org|2.77"`
- Status and name metadata are TermX-internal and not included in the export

When `fhirVersions` is not specified, a default R5 entry is generated using `rootUrl`.

## Import

`POST /api/terminology-servers/import/ecosystem`

Accepts a FHIR ecosystem registry JSON body. For each server in the file:
- Creates a new server if no server with that code exists
- Updates the existing server if one with the same code is found (preserving headers, authConfig, and currentInstallation settings)
- Parses `url|version` canonical strings back into enriched authoritative entries

## UI

### Edit Form
The server edit form includes a "FHIR Ecosystem" section with:
- Text inputs for accessInfo, usage, operations, FHIR versions
- Autocomplete search for adding authoritative CodeSystems and ValueSets from the local TermX instance
- Manual URL input for adding external canonical URL patterns

### List Page
The "..." menu on the server list page provides:
- "Export to FHIR Ecosystem format" -- downloads the ecosystem JSON file
- "Import from FHIR Ecosystem file" -- uploads and imports an ecosystem JSON file
