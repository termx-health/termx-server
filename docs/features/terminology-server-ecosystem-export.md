# Export TermX Servers to FHIR Ecosystem Registry Format

## Feature Description

This feature allows you to export your TermX terminology servers in the **FHIR Terminology Ecosystem Registry** JSON format, as defined by the [HL7 FHIR Terminology Ecosystem IG](https://build.fhir.org/ig/HL7/fhir-tx-ecosystem-ig/ecosystem.html).

## Purpose

- Export your TermX servers for registration in the HL7 FHIR Terminology Ecosystem
- Share your terminology server registry with other organizations
- Create a backup of your server configuration in a standardized format
- Publish your servers for discovery by other FHIR implementations

## API Endpoint

### Export Servers

**GET** `/servers/export/ecosystem`

**Authorization:** `Server.read` privilege required

**Query Parameters:**
- `download` (optional, boolean) - If `true`, returns response as downloadable file

**Response:**
- Content-Type: `application/json`
- Format: FHIR Ecosystem server registry JSON

## Usage Examples

### View Export in Browser

```bash
curl http://localhost:8200/servers/export/ecosystem
```

### Download as File

```bash
curl "http://localhost:8200/servers/export/ecosystem?download=true" -o termx-servers.json
```

### Using in Scripts

```bash
# Export and save
EXPORT_URL="http://localhost:8200/servers/export/ecosystem?download=true"
curl "$EXPORT_URL" -o termx-ecosystem-registry.json

# Verify the file
jq . termx-ecosystem-registry.json
```

## Output Format

The export follows the FHIR Ecosystem server registry specification:

```json
{
  "formatVersion": "1",
  "description": "TermX Terminology Servers",
  "servers": [{
    "code": "xxx",
    "name": "Terminoloogiaserver",
    "url": "https://server.example.org",
    "open": true,
    "authoritative": [],
    "authoritative-valuesets": [],
    "authoritative-conceptmaps": [],
    "authoritative-structuredefinitions": [],
    "authoritative-structuremaps": [],
    "exclusions": [],
    "fhirVersions": [{
      "version": "R5",
      "url": "https://server.example.org"
    }]
  }]
}
```

### Fields Exported

**Per Server:**
- `code` - Server code from TermX
- `name` - Server name (English if available, otherwise first available language)
- `url` - Root URL of the server
- Access method indicators:
  - `open: true` - If no authentication required
  - `token: true` - If token authentication configured (Authorization header)
  - `oauth: true` - If OAuth authentication configured
- `access_info` - Human-readable authentication information (when applicable)
- `fhirVersions` - Array of FHIR versions supported (per-server; e.g. R3/R4/R4B/R5/R6)
- `authoritative` - Code systems where the server is authoritative (resolved from the server's authoritative-resource patterns)
- `authoritative-valuesets` - Value sets where the server is authoritative (resolved from the server's authoritative-resource patterns)

> These fields are populated from the server's configured authoritative-resource patterns and
> supported FHIR versions. See [`fhir-terminology-ecosystem-fields.md`](fhir-terminology-ecosystem-fields.md)
> for the authoritative field-level reference.

### Filter Behavior

Only **active** servers are included in the export. Inactive servers are automatically excluded.

## Use Cases

### 1. Register with HL7 Ecosystem

Export your servers and register them with the HL7 FHIR Terminology Ecosystem:

```bash
# Export your servers
curl "http://localhost:8200/servers/export/ecosystem?download=true" -o my-servers.json

# Host the file at a public URL
# Then submit the URL to HL7 for inclusion in the master registry
```

### 2. Share with Partners

Export and share your server list with partner organizations:

```bash
# Export
curl "http://localhost:8200/servers/export/ecosystem?download=true" -o servers.json

# Share the JSON file with partners
# They can import it or use it for discovery
```

### 3. Backup Configuration

Create regular backups of your server configuration:

```bash
# Automated backup script
#!/bin/bash
DATE=$(date +%Y%m%d)
curl "http://localhost:8200/servers/export/ecosystem?download=true" \
  -o "backups/termx-servers-$DATE.json"
```

### 4. Documentation

Generate documentation about available servers:

```bash
# Export and format
curl "http://localhost:8200/servers/export/ecosystem" | jq '.'

# Extract server names
curl "http://localhost:8200/servers/export/ecosystem" | \
  jq -r '.servers[] | .name'
```

## Web UI Access

In the admin interface at `http://localhost:4200/terminology-servers`, the "..." menu on the
server list page provides **Export to FHIR Ecosystem format** (and **Import from FHIR Ecosystem
file**), which call these endpoints. See
[`fhir-terminology-ecosystem-fields.md`](fhir-terminology-ecosystem-fields.md) for the UI reference.

## Technical Details

### Implementation

**Service:** `TerminologyServerService.exportToEcosystemFormat()`
- Queries all active terminology servers from database
- Converts each server to ecosystem format
- Returns JSON string

**Controller:** `TerminologyServerController.exportEcosystem()`
- Exposes HTTP endpoint
- Adds download header when requested
- Sets proper content type

### Conversion Logic

**Authentication Detection:**
1. OAuth configured → `oauth: true` + access info
2. Authorization header present → `token: true` + access info  
3. Neither → `open: true`

**Name Extraction:**
- Prefers English name (`en`)
- Falls back to first available language
- `name` is `null` (omitted) when the server has no name

**FHIR Endpoint:**
- When `fhirVersions` is not configured, a single default entry is emitted with
  `version: "R5"` and `url` set to the server's root URL verbatim (no `/fhir` is appended)

## Limitations

The following are **implemented** and no longer limitations:

- **FHIR versions** are configurable per server (R3/R4/R4B/R5/R6), not fixed to R4.
- **Authoritative code systems / value sets** are populated from each server's configured
  authoritative-resource patterns, resolved against TermX's own resources.

Still open / possible future work:

1. **Usage Tags** — usage categorization (publication, validation, code-generation) is not yet emitted.
2. **Content Level** — content-level indicators (complete, fragment, etc.) for candidate servers are
   not yet specified.

## Future Enhancements

### Auto-Discovery

Automatically populate authoritative lists by:
- Querying CodeSystem resources on the server
- Querying ValueSet resources on the server
- Extracting canonical URLs

## Security Considerations

**Sensitive Data:**
- OAuth client secrets are **not** exported (masked by `maskSensitiveData()`)
- Authorization header values are **not** exported
- Only `access_info` text is included (no credentials)

**Access Control:**
- Requires `Server.read` privilege
- Admin users have access by default
- Configure additional users in `mock/users.json`

## Troubleshooting

**Issue:** Export returns empty servers array

**Solution:** Check that you have active servers configured:
```bash
curl http://localhost:8200/servers
```

---

**Issue:** Permission denied (403)

**Solution:** Ensure you have admin privileges. See `application-local.yml`:
```yaml
auth:
  mock:
    default-user: admin  # Required for Server.read
```

---

**Issue:** Server `name` is `null` / missing in the export

**Solution:** Add a name to your server in the TermX admin UI at `http://localhost:4200/terminology-servers`

## Related Documentation

- [FHIR Terminology Ecosystem IG](https://build.fhir.org/ig/HL7/fhir-tx-ecosystem-ig/ecosystem.html)
- [FHIR Terminology Ecosystem Integration](fhir-terminology-ecosystem-registry-proxy.md) - Discovery and Resolution API
- [Terminology Server Management](../../README.md) - Managing internal servers

## Example Output

```json
{
  "formatVersion": "1",
  "description": "TermX Terminology Servers",
  "servers": [
    {
      "code": "xxx",
      "name": "Terminoloogiaserver",
      "url": "https://server.example.org",
      "open": true,
      "authoritative": [],
      "authoritative-valuesets": [],
      "authoritative-conceptmaps": [],
      "authoritative-structuredefinitions": [],
      "authoritative-structuremaps": [],
      "exclusions": [],
      "fhirVersions": [
        {
          "version": "R5",
          "url": "https://server.example.org"
        }
      ]
    }
  ]
}
```

## Summary

✅ Export TermX servers to FHIR Ecosystem format  
✅ Download as JSON file  
✅ Standards-compliant output  
✅ Secure (no credentials exported)  
✅ Ready for HL7 ecosystem registration  

**Available Now:** `GET /servers/export/ecosystem`
