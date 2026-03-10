# FHIR Terminology Ecosystem - Quick Reference

> **One-page guide to discovering and resolving terminology servers**

## 🎯 What Is It?

A feature that helps you find the right terminology server for any clinical code system or value set in the HL7 FHIR ecosystem.

## 🔗 Access Points

| Interface | URL | Purpose |
|-----------|-----|---------|
| **Web UI** | https://dev.termx.org/tx-ecosystem/ | Visual, interactive interface |
| **API** | https://dev.termx.org/tx-reg | Programmatic access |

## 📋 Common Use Cases

### 1. Find All Available Servers
```bash
curl https://dev.termx.org/tx-reg
```
Or visit the Web UI → Discovery tab → Click "Discover Servers"

### 2. Find R4 Servers Only
```bash
curl https://dev.termx.org/tx-reg?fhirVersion=R4
```
Or Web UI → Discovery tab → Select "R4" → Click "Discover Servers"

### 3. Find SNOMED CT Server
```bash
curl "https://dev.termx.org/tx-reg/resolve?fhirVersion=R4&url=http://snomed.info/sct"
```
Or Web UI → Resolution tab → Enter SNOMED URL → Click "Resolve Server"

### 4. Find LOINC Server
```bash
curl "https://dev.termx.org/tx-reg/resolve?fhirVersion=R4&url=http://loinc.org"
```

### 5. Download Server List
```bash
curl "https://dev.termx.org/tx-reg?download=true" -o servers.json
```
Or Web UI → Discovery tab → Click "⬇️ Download Results"

## 🔍 API Quick Reference

### Discovery Endpoint

**GET** `/tx-reg`

**Query Parameters:**
- `fhirVersion` - R3, R4, R4B, R5, R6
- `registry` - Registry code
- `server` - Server code
- `url` - CodeSystem URL
- `authoritativeOnly` - true/false
- `download` - true/false

**Example Response:**
```json
{
  "last-update": "2026-03-10T12:00:00Z",
  "results": [
    {
      "server-name": "HL7 Terminology Server",
      "url": "http://tx.fhir.org/r4",
      "fhirVersion": "4.0.1",
      "systems": 150,
      "open": true
    }
  ]
}
```

### Resolution Endpoint

**GET** `/tx-reg/resolve`

**Query Parameters (required):**
- `fhirVersion` - **Required**: R3, R4, R4B, R5, R6
- `url` **OR** `valueSet` - **One required**: CodeSystem or ValueSet URL

**Query Parameters (optional):**
- `authoritativeOnly` - true/false
- `usage` - publication, validation, code-generation
- `download` - true/false

**Example Response:**
```json
{
  "authoritative": [
    {
      "server-name": "SNOMED International",
      "url": "https://snowstorm.snomed.org/fhir",
      "fhirVersion": "4.0.1",
      "open": true
    }
  ],
  "candidate": []
}
```

## 🎨 Web UI Features

### Discovery Tab
- Filter by FHIR version, registry, server, or CodeSystem
- Toggle "Show only authoritative servers"
- View results in interactive table
- Download results as JSON

### Resolution Tab
- Enter CodeSystem or ValueSet URL
- Select FHIR version (required)
- Optional: filter by usage type
- View authoritative and candidate servers separately
- Download resolution results

## 💡 Quick Tips

### For Developers
```javascript
// Find server in JavaScript
const response = await fetch('/tx-reg/resolve?fhirVersion=R4&url=http://snomed.info/sct');
const data = await response.json();
const serverUrl = data.authoritative[0].url;
// Use serverUrl for terminology operations
```

### For IG Publishers
```bash
# Add to your build script
SNOMED_SERVER=$(curl -s "https://dev.termx.org/tx-reg/resolve?fhirVersion=R4&url=http://snomed.info/sct" | jq -r '.authoritative[0].url')
echo "Using SNOMED server: $SNOMED_SERVER"
```

### For Testing
```bash
# Find all test servers
curl "https://dev.termx.org/tx-reg?fhirVersion=R4" | jq '.results[] | {name: .["server-name"], url: .url}'
```

## 📊 Common Code Systems

| Code System | URL |
|-------------|-----|
| SNOMED CT | `http://snomed.info/sct` |
| LOINC | `http://loinc.org` |
| RxNorm | `http://www.nlm.nih.gov/research/umls/rxnorm` |
| ICD-10 | `http://hl7.org/fhir/sid/icd-10` |
| UCUM | `http://unitsofmeasure.org` |

## 🚀 Integration Examples

### cURL
```bash
curl "https://dev.termx.org/tx-reg/resolve?fhirVersion=R4&url=http://snomed.info/sct"
```

### JavaScript/Node.js
```javascript
const axios = require('axios');

async function findServer(codeSystem) {
  const response = await axios.get('/tx-reg/resolve', {
    params: { fhirVersion: 'R4', url: codeSystem }
  });
  return response.data.authoritative[0].url;
}
```

### Python
```python
import requests

def find_server(code_system):
    response = requests.get(
        'https://dev.termx.org/tx-reg/resolve',
        params={'fhirVersion': 'R4', 'url': code_system}
    )
    return response.json()['authoritative'][0]['url']
```

### Java
```java
String apiUrl = "https://dev.termx.org/tx-reg/resolve?fhirVersion=R4&url=http://snomed.info/sct";
HttpRequest request = HttpRequest.newBuilder()
    .uri(URI.create(apiUrl))
    .build();
HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
JsonNode result = objectMapper.readTree(response.body());
String serverUrl = result.get("authoritative").get(0).get("url").asText();
```

## ⚙️ Configuration

**Default (No Configuration Needed):**
Uses HL7 public coordination server: `http://tx.fhir.org/tx-reg`

**Custom Server (Optional):**
```yaml
# application.yml
terminology-ecosystem:
  coordination-server-url: https://custom.server/tx-reg
```

**Environment Variable:**
```bash
export TERMINOLOGY_ECOSYSTEM_URL=https://custom.server/tx-reg
```

## 🔐 Security

- **Authentication:** None required (public API)
- **CORS:** Enabled for cross-origin requests
- **HTTPS:** Supported and recommended
- **Rate Limiting:** None (relies on coordination server)

## 📖 Full Documentation

| Document | Purpose |
|----------|---------|
| [Feature Description](fhir-terminology-ecosystem-feature-description.md) | Business overview, use cases |
| [Technical Guide](fhir-terminology-ecosystem-api.md) | API reference, testing |
| [UI Guide](fhir-terminology-ecosystem-ui.md) | Web interface documentation |
| [Implementation Summary](fhir-terminology-ecosystem-api-implementation-summary.md) | Technical implementation details |

## 🆘 Troubleshooting

**Problem:** 404 error on `/tx-ecosystem/`  
**Solution:** Verify application is running and static resources are configured

**Problem:** API returns 502 (Bad Gateway)  
**Solution:** Coordination server may be unavailable, try again later

**Problem:** Empty results  
**Solution:** Adjust filters, try broader search criteria

**Problem:** Required parameter error  
**Solution:** Ensure `fhirVersion` is provided for resolution endpoint

## ✅ Status

- **Backend API:** ✅ Production Ready
- **Frontend UI:** ✅ Production Ready  
- **Documentation:** ✅ Complete
- **Testing:** ✅ 30+ Integration Tests
- **Standards Compliance:** ✅ HL7 FHIR Ecosystem IG

## 🎓 Learning Resources

1. **Start Here:** Web UI at https://dev.termx.org/tx-ecosystem/
2. **Try Examples:** Use cURL commands above
3. **Read Specs:** https://build.fhir.org/ig/HL7/fhir-tx-ecosystem-ig/
4. **Explore Code:** See `termx-core/src/main/java/org/termx/core/sys/server/`

---

**Need Help?** See full documentation in `docs/features/` directory.
