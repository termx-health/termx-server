# Ecosystem Management

## Description

Ecosystem Management provides CRUD operations for FHIR TX Ecosystem configurations ([FHIR TX Ecosystem IG](https://build.fhir.org/ig/HL7/fhir-tx-ecosystem-ig/ecosystem.html)). Each ecosystem groups existing Terminology Servers into a named collection and exposes them via a public API in the ecosystem.json format for consumption by HTX Router.

Key capabilities:
- Create/edit ecosystems with code, name, description, and format version
- Select terminology servers via multiselect (many-to-many relationship)
- Expose ecosystems via public API (`/public/ecosystems/{code}`) returning ecosystem.json format
- View and download ecosystem.json from the list view

## Configuration

No server-side configuration required. Ecosystems are managed through the UI and REST API. Uses Space privileges (`Space.view`, `Space.edit`).

## Use-Cases

### Scenario 1: Create an ecosystem

1. Navigate to Management > Ecosystems
2. Click "Add ecosystem", enter code, name, description
3. Select terminology servers from the multiselect dropdown
4. Save — ecosystem appears in the list with server tags

### Scenario 2: HTX Router loads ecosystem from TermX

1. In HTX Router's `ecosystem.json`, add a placeholder server with `ecosystem_url`:
   ```json
   {"code": "termx-eco", "name": "TermX Ecosystem", "ecosystem_url": "https://termx.example.org/public/ecosystems/my-ecosystem"}
   ```
2. On startup, the router fetches the ecosystem definition and merges the servers

### Scenario 3: View/download ecosystem.json

1. In the ecosystem list, click the file icon to view ecosystem.json in a new tab
2. Click the download icon to save as a file

## API

### Internal API (secured, requires Space privileges)

| Method | Path | Privilege | Description |
|--------|------|-----------|-------------|
| POST | `/ecosystems` | Space.edit | Create ecosystem |
| PUT | `/ecosystems/{id}` | Space.edit | Update ecosystem |
| GET | `/ecosystems/{id}` | Space.view | Load ecosystem |
| GET | `/ecosystems` | Space.view | Search ecosystems (textContains) |

### Public API (unsecured, under /public prefix)

| Method | Path | Description |
|--------|------|-------------|
| GET | `/public/ecosystems` | List active ecosystems (code, names) |
| GET | `/public/ecosystems/{code}` | Full ecosystem.json in IG format + envelope |
| GET | `/public/ecosystems/{code}/server/{server-code}` | Single server definition |

Public response format (`GET /public/ecosystems/{code}`):
```json
{
  "formatVersion": "1",
  "description": "...",
  "servers": [
    {
      "code": "dev",
      "name": "Dev Server",
      "fhirVersions": [{"version": "R5", "url": "https://dev.termx.org/api/fhir"}],
      "authoritative": ["http://example.org/CodeSystem/*"],
      "authoritative-valuesets": ["http://example.org/ValueSet/*"]
    }
  ]
}
```

Returns ecosystem.json content directly in FHIR TX Ecosystem IG format — compatible with HTX Router's `ecosystem_url` field.

## Testing

```bash
# Create ecosystem
curl -X POST http://localhost:8200/ecosystems \
  -H "Content-Type: application/json" \
  -d '{"code":"test","names":{"en":"Test"},"active":true,"serverIds":[1,2]}'

# Load via public API (by code)
curl http://localhost:8200/public/ecosystems/test

# Load single server
curl http://localhost:8200/public/ecosystems/test/server/dev
```

## Data Model

### sys.ecosystem

| Field | Type | Description |
|-------|------|-------------|
| id | bigint | Auto-generated PK (core.s_entity) |
| code | text | Unique business key |
| names | jsonb | Localized names |
| format_version | text | Fixed "1" (FHIR TX IG spec) |
| description | text | Free text |
| active | boolean | Default true |

### sys.ecosystem_server (join table)

| Field | Type | Description |
|-------|------|-------------|
| id | bigserial | Auto-generated PK |
| ecosystem_id | bigint FK | References sys.ecosystem |
| server_id | bigint FK | References sys.terminology_server |

## Architecture

```
┌─────────────┐     ┌──────────────────────┐     ┌─────────────┐
│  termx-web  │────▶│     termx-server      │────▶│  PostgreSQL  │
│  (Angular)  │     │  /ecosystems (CRUD)   │     │ sys.ecosystem│
└─────────────┘     │  /public/ecosystems   │     │ sys.ecosystem│
                    │    (public JSON)      │     │   _server    │
                    └──────────┬────────────┘     └─────────────┘
                               │
                    /public/ecosystems/{code}
                               │
                    ┌──────────▼────────────┐
                    │      htx-router       │
                    │   (ecosystem_url)     │
                    └───────────────────────┘
```

## Technical Implementation

### Source files

| File | Description |
|------|-------------|
| `termx-api/.../sys/ecosystem/Ecosystem.java` | DTO with serverIds |
| `termx-api/.../sys/ecosystem/EcosystemQueryParams.java` | Query parameters |
| `termx-api/.../sys/ecosystem/EcosystemPublicResponse.java` | Public API envelope |
| `termx-core/.../sys/ecosystem/EcosystemRepository.java` | DB access + ecosystem_server join |
| `termx-core/.../sys/ecosystem/EcosystemService.java` | Business logic |
| `termx-core/.../sys/ecosystem/EcosystemController.java` | Internal secured API |
| `termx-core/.../sys/ecosystem/EcosystemPublicController.java` | Public unsecured API |
| `sys/changelog/sys/80-ecosystem.sql` | Liquibase migration |

### Relationship to TerminologyServer

Ecosystem references servers by ID via `sys.ecosystem_server` join table. The public API builds ecosystem.json dynamically by calling `TerminologyServerService.convertToEcosystemServer()` for each referenced server.
