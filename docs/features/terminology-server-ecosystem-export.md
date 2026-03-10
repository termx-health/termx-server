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

**GET** `/api/terminology-servers/export/ecosystem`

**Authorization:** `TerminologyServer.view` privilege required

**Query Parameters:**
- `download` (optional, boolean) - If `true`, returns response as downloadable file

**Response:**
- Content-Type: `application/json`
- Format: FHIR Ecosystem server registry JSON

## Usage Examples

### View Export in Browser

```bash
curl http://localhost:8200/api/terminology-servers/export/ecosystem
```

### Download as File

```bash
curl "http://localhost:8200/api/terminology-servers/export/ecosystem?download=true" -o termx-servers.json
```

### Using in Scripts

```bash
# Export and save
EXPORT_URL="http://localhost:8200/api/terminology-servers/export/ecosystem?download=true"
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
    "fhirVersions": [{
      "version": "R4",
      "url": "https://server.example.org/fhir"
    }],
    "authoritative": [],
    "authoritative-valuesets": [],
    "candidate": [],
    "exclusions": []
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
- `fhirVersions` - Array of FHIR versions supported (currently defaults to R4)
- `authoritative` - Code systems where server is authoritative (currently empty, for future enhancement)
- `authoritative-valuesets` - Value sets where server is authoritative (currently empty, for future enhancement)

### Filter Behavior

Only **active** servers are included in the export. Inactive servers are automatically excluded.

## Use Cases

### 1. Register with HL7 Ecosystem

Export your servers and register them with the HL7 FHIR Terminology Ecosystem:

```bash
# Export your servers
curl "http://localhost:8200/api/terminology-servers/export/ecosystem?download=true" -o my-servers.json

# Host the file at a public URL
# Then submit the URL to HL7 for inclusion in the master registry
```

### 2. Share with Partners

Export and share your server list with partner organizations:

```bash
# Export
curl "http://localhost:8200/api/terminology-servers/export/ecosystem?download=true" -o servers.json

# Share the JSON file with partners
# They can import it or use it for discovery
```

### 3. Backup Configuration

Create regular backups of your server configuration:

```bash
# Automated backup script
#!/bin/bash
DATE=$(date +%Y%m%d)
curl "http://localhost:8200/api/terminology-servers/export/ecosystem?download=true" \
  -o "backups/termx-servers-$DATE.json"
```

### 4. Documentation

Generate documentation about available servers:

```bash
# Export and format
curl "http://localhost:8200/api/terminology-servers/export/ecosystem" | jq '.'

# Extract server names
curl "http://localhost:8200/api/terminology-servers/export/ecosystem" | \
  jq -r '.servers[] | .name'
```

## Web UI Access (Future Enhancement)

In the admin interface at `http://localhost:4200/terminology-servers`, you can add an **Export** button that calls this endpoint and downloads the file.

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
- Defaults to "Unknown Server" if no name

**FHIR Endpoint:**
- Appends `/fhir` to root URL
- Handles trailing slashes correctly

## Limitations

### Current Version

1. **FHIR Version:** Currently defaults to R4 only
   - Future: Allow configuration of supported FHIR versions per server

2. **Authoritative Systems:** Empty arrays for now
   - Future: Populate from CodeSystem/ValueSet metadata
   - Will need to track which systems each server is authoritative for

3. **Usage Tags:** Not implemented
   - Future: Add usage categorization (publication, validation, code-generation)

4. **Content Level:** Not specified for candidate servers
   - Future: Add content level indicators (complete, fragment, etc.)

## Future Enhancements

### Phase 1: Enhanced Metadata

Add fields to `TerminologyServer` model:
```java
private List<String> authoritativeCodeSystems;
private List<String> authoritativeValueSets;
private List<String> fhirVersions;
private List<String> usageTags;
```

### Phase 2: Auto-Discovery

Automatically populate authoritative lists by:
- Querying CodeSystem resources on the server
- Querying ValueSet resources on the server
- Extracting canonical URLs

### Phase 3: Multi-Version Support

Support multiple FHIR versions per server:
```json
"fhirVersions": [
  {"version": "R4", "url": "https://server/r4"},
  {"version": "R5", "url": "https://server/r5"}
]
```

## Security Considerations

**Sensitive Data:**
- OAuth client secrets are **not** exported (masked by `maskSensitiveData()`)
- Authorization header values are **not** exported
- Only `access_info` text is included (no credentials)

**Access Control:**
- Requires `TerminologyServer.view` privilege
- Admin users have access by default
- Configure additional users in `mock/users.json`

## Troubleshooting

**Issue:** Export returns empty servers array

**Solution:** Check that you have active servers configured:
```bash
curl http://localhost:8200/api/terminology-servers
```

---

**Issue:** Permission denied (403)

**Solution:** Ensure you have admin privileges. See `application-local.yml`:
```yaml
auth:
  mock:
    default-user: admin  # Required for TerminologyServer.view
```

---

**Issue:** Names show as "Unknown Server"

**Solution:** Add a name to your server in the TermX admin UI at `http://localhost:4200/terminology-servers`

## Related Documentation

- [FHIR Terminology Ecosystem IG](https://build.fhir.org/ig/HL7/fhir-tx-ecosystem-ig/ecosystem.html)
- [FHIR Terminology Ecosystem Integration](fhir-terminology-ecosystem-api.md) - Discovery and Resolution API
- [Terminology Server Management](../README.md) - Managing internal servers

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
      "fhirVersions": [
        {
          "version": "R4",
          "url": "https://server.example.org/fhir"
        }
      ],
      "authoritative": [],
      "authoritative-valuesets": [],
      "candidate": [],
      "exclusions": []
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

**Available Now:** `GET /api/terminology-servers/export/ecosystem`
