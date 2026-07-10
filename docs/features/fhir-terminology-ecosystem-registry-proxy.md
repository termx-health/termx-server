# FHIR Terminology Ecosystem Registry Proxy

## Abstract

TermX exposes the HL7 [FHIR Terminology Ecosystem](https://build.fhir.org/ig/HL7/fhir-tx-ecosystem-ig/ecosystem.html) coordination registry as a thin passthrough proxy. It serves two public JSON endpoints — `/tx-reg` (discovery: list registered terminology servers) and `/tx-reg/resolve` (resolution: find the authoritative server for a given CodeSystem or ValueSet) — plus a static web UI at `/tx-ecosystem/`. Requests are forwarded verbatim to a coordination server (by default `tx.fhir.org/tx-reg`) and the JSON response is returned unchanged, so TermX stays fully compliant with the IG and does not drift as the upstream API evolves. This is the **consumer** side of the ecosystem: it lets clients *discover* other servers, not register TermX itself. TermX is the host that serves these paths — this is distinct from the HTX Router (`tx.hl7.lt`), which exposes a different, operational routing API.

## For business users

The feature answers one question: *"Which terminology server should I use for this code system or value set, according to the HL7 coordination registry?"* In distributed terminology environments, different servers host different content (SNOMED CT, LOINC, ICD-10, RxNorm, national SNOMED editions, and so on). Rather than hard-coding server addresses, clients can discover what is available and resolve the authoritative source at runtime.

**Value:**

- Route terminology operations (validation, expansion, translation) to the correct authoritative server instead of maintaining a manual server inventory.
- Give IG publishers a reliable way to find the right server for each referenced code system during a build.
- Support version-aware discovery (R3, R4, R4B, R5, R6) and jurisdiction-specific SNOMED editions.
- Provide a self-service web UI for terminology specialists to browse and export the ecosystem without writing code.

**Representative use cases:**

- **Clinical system integration** — an EHR resolves the authoritative SNOMED CT server and routes all code validation there.
- **IG publishing** — a build script resolves each referenced code system to its server before validating.
- **Terminology research** — a specialist uses the web UI to compare coverage across servers and exports the list as JSON.
- **Multi-jurisdiction routing** — a global platform resolves country-specific SNOMED edition URLs to the correct national server.

### Relationship to HTX Router and HTX Viewer

TermX's `tx-reg` / `tx-ecosystem` feature is the **coordination-discovery** layer: it answers who is authoritative in the HL7 catalog. A separate operational layer answers a different question — *"How do our apps and users reach our own configured terminology servers?"* — and is provided by the sibling **HTX platform**:

- **HTX Router** (gateway, demo at `tx.hl7.lt`) is a single FHIR terminology entry point that routes `$lookup`, `$expand`, `$validate-code`, search, and resource reads to the correct backend using an `ecosystem.json` registry. It adds server-side caching, version-aware routing, and observability. This is a **different API** from TermX's `/tx-reg` proxy.
- **HTX Viewer** (browser, demo at `htx.hl7.ee`) is an Angular UI over the gateway, offering landing-page summaries and browse/search of CodeSystems and ValueSets.

| Aspect | TermX `tx-reg` / `tx-ecosystem` | HTX Router + Viewer |
|--------|----------------------------------|---------------------|
| Primary question | Who is authoritative in the HL7 coordination catalog? | How do our apps/users reach our configured servers? |
| Configuration | Optional URL to a coordination server (default HL7) | `ecosystem.json` + env (e.g. `FHIR_ECOSYSTEM_CONFIG`) |
| UI | Lightweight discovery/resolution at `/tx-ecosystem/` | Full dashboard + browse (Viewer) |
| State | Stateless proxy toward the coordination server | Server-side caching and routing policies |

The two are complementary: coordination discovery (TermX) plus gateway and browser (HTX) together cover both global-registry and local-operations needs.

For related in-repo terminology-ecosystem features, see [ecosystem-management.md](ecosystem-management.md) and [fhir-terminology-ecosystem-fields.md](fhir-terminology-ecosystem-fields.md).

## Deployment / operations

### Configuration

| Property | Env variable | Default | Description |
|----------|-------------|---------|-------------|
| `terminology-ecosystem.coordination-server-url` | `TERMINOLOGY_ECOSYSTEM_URL` | `http://tx.fhir.org/tx-reg` | Base URL of the FHIR Terminology Ecosystem coordination server that requests are proxied to |

The feature is enabled by default and needs no configuration to run against the HL7 public server. Endpoints are registered automatically at application startup.

Configuration resolution order: environment variable `TERMINOLOGY_ECOSYSTEM_URL` → property `terminology-ecosystem.coordination-server-url` → default `http://tx.fhir.org/tx-reg`.

**Default `application.yml`:**

```yaml
terminology-ecosystem:
  coordination-server-url: ${TERMINOLOGY_ECOSYSTEM_URL:http://tx.fhir.org/tx-reg}

auth:
  public:
    endpoints:
      - '/tx-reg'         # API — no authentication required
      - '/tx-ecosystem'   # static web UI — no authentication required

micronaut:
  router:
    static-resources:
      tx-ecosystem:
        paths: classpath:static/tx-ecosystem
        mapping: /tx-ecosystem/**
```

**Point at a custom coordination server:**

```bash
export TERMINOLOGY_ECOSYSTEM_URL=https://custom.coordination.server/tx-reg
./gradlew :termx-app:run
```

or in `application.yml`:

```yaml
terminology-ecosystem:
  coordination-server-url: https://custom.coordination.server/tx-reg
```

### How it is served

- **API** (`/tx-reg`, `/tx-reg/resolve`) is a REST controller that proxies to the coordination server and returns the JSON response unchanged.
- **Web UI** (`/tx-ecosystem/`) is a single self-contained HTML file served as a Micronaut static resource — no build step, no external assets.
- Both paths are registered as public endpoints; the `AuthorizationFilter` allows unauthenticated access to any path under those prefixes, mirroring the public behavior of `tx.fhir.org`.

### Runtime requirements and characteristics

- Network access from TermX to the coordination server is required. If it is unreachable the API returns **HTTP 502**.
- The proxy is **stateless**: no caching or local registry. Every request hits the coordination server, so latency tracks the upstream (typically a couple of seconds). Concurrency is bounded only by the coordination server.
- No new dependencies were introduced; the feature reuses the existing HTTP client and JSON utilities.

### Deployment verification

After deploying, confirm the endpoints respond and are public:

```bash
# Discovery
curl https://dev.termx.org/tx-reg | jq

# Resolution
curl "https://dev.termx.org/tx-reg/resolve?fhirVersion=R4&url=http://snomed.info/sct" | jq

# Public access — should return 200, not 401/403
curl -I https://dev.termx.org/tx-reg
```

### Known limitations

- **External dependency** — requires coordination-server availability; no fallback or offline mode.
- **Single coordination server** — queries one configured server; does not aggregate multiple registries.
- **No caching** — simple and reliable, but every call adds a round-trip to the upstream.

## Technical details

All endpoints are publicly accessible (no authentication) and based at `/tx-reg`. Responses are returned exactly as received from the coordination server.

### Discovery endpoint

Lists registered terminology servers with optional filtering.

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/tx-reg` | Public | Query terminology servers with optional filters |

**Query parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `registry` | String | No | Filter by registry code |
| `server` | String | No | Filter by server code |
| `fhirVersion` | String | No | Filter by FHIR version (R3, R4, R4B, R5, R6) |
| `url` | String | No | Filter servers that host a specific CodeSystem URL |
| `authoritativeOnly` | Boolean | No | Return only authoritative servers (default: false) |
| `download` | Boolean | No | Return as a downloadable `tx-servers.json` file (default: false) |

**Examples:**

```bash
# List all servers
curl https://dev.termx.org/tx-reg

# List only R4 servers
curl "https://dev.termx.org/tx-reg?fhirVersion=R4"

# Find servers hosting SNOMED CT
curl "https://dev.termx.org/tx-reg?url=http://snomed.info/sct"

# Find authoritative servers for LOINC
curl "https://dev.termx.org/tx-reg?url=http://loinc.org&authoritativeOnly=true"

# Download the server list
curl "https://dev.termx.org/tx-reg?download=true" -o servers.json
```

**Response format:**

```json
{
  "last-update": "2026-03-10T12:00:00Z",
  "master-url": "https://fhir.github.io/ig-registry/tx-servers.json",
  "results": [
    {
      "server-name": "SNOMED International",
      "server-code": "snowstorm.snomed.org",
      "registry-name": "HL7 FHIR Terminology Registry",
      "registry-code": "hl7-main",
      "registry-url": "https://fhir.github.io/ig-registry/tx-servers.json",
      "url": "https://snowstorm.snomed.org/fhir",
      "fhirVersion": "4.0.1",
      "error": null,
      "last-success": 15000,
      "systems": 1,
      "authoritative": [
        "http://snomed.info/sct|http://snomed.info/sct/900000000000207008"
      ],
      "authoritative-valuesets": [],
      "candidate": [],
      "candidate-valuesets": [],
      "open": true
    }
  ]
}
```

**Discovery response fields:**

Root object:

| Field | Type | Description |
|-------|------|-------------|
| `last-update` | String (ISO 8601) | Last time registries were scanned |
| `master-url` | String (URL) | Master registry URL |
| `results` | Array of Server | Discovered servers |

Server object:

| Field | Type | Description |
|-------|------|-------------|
| `server-name` | String | Human-readable server name |
| `server-code` | String | Unique persistent server identifier |
| `registry-name` | String | Name of the registry this server belongs to |
| `registry-code` | String | Persistent registry identifier |
| `registry-url` | String (URL) | Registry definition URL |
| `url` | String (URL) | FHIR endpoint URL |
| `fhirVersion` | String | FHIR version (semver, e.g. `4.0.1`) |
| `error` | String or null | Error from last scan, null if successful |
| `last-success` | Integer | Milliseconds since last successful scan |
| `systems` | Integer | Number of CodeSystems on the server |
| `authoritative` | Array of String | Canonical URLs of authoritative CodeSystems |
| `authoritative-valuesets` | Array of String | Canonical URLs of authoritative ValueSets |
| `candidate` | Array of String | Canonical URLs of candidate CodeSystems |
| `candidate-valuesets` | Array of String | Canonical URLs of candidate ValueSets |
| `open` | Boolean | True if the server supports unauthenticated access |
| `password`, `token`, `oauth`, `smart`, `cert` | Boolean | Supported authentication methods |

### Resolution endpoint

Recommends which server to use for a specific CodeSystem or ValueSet.

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/tx-reg/resolve` | Public | Resolve the authoritative server for a CodeSystem or ValueSet |

**Query parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `fhirVersion` | String | **Yes** | FHIR version (R3, R4, R4B, R5, R6) |
| `url` | String | One of `url`/`valueSet` | CodeSystem canonical URL (`url` or `url\|version`) |
| `valueSet` | String | One of `url`/`valueSet` | ValueSet canonical URL (`url` or `url\|version`) |
| `authoritativeOnly` | Boolean | No | Return only authoritative servers (default: false) |
| `usage` | String | No | Usage token (publication, validation, code-generation) |
| `download` | Boolean | No | Return as a downloadable file (default: false) |

Missing `fhirVersion`, or missing both `url` and `valueSet`, returns **HTTP 400**.

**Examples:**

```bash
# Find the server for SNOMED CT
curl "https://dev.termx.org/tx-reg/resolve?fhirVersion=R4&url=http://snomed.info/sct"

# Find the server for a specific SNOMED edition/version
curl "https://dev.termx.org/tx-reg/resolve?fhirVersion=R4&url=http://snomed.info/sct|http://snomed.info/sct/900000000000207008/version/20230901"

# Find the server for a ValueSet
curl "https://dev.termx.org/tx-reg/resolve?fhirVersion=R4&valueSet=http://hl7.org/fhir/ValueSet/administrative-gender"

# Authoritative servers only
curl "https://dev.termx.org/tx-reg/resolve?fhirVersion=R4&url=http://loinc.org&authoritativeOnly=true"

# Download the resolution result
curl "https://dev.termx.org/tx-reg/resolve?fhirVersion=R4&url=http://snomed.info/sct&download=true" -o resolution.json
```

**Response format:**

```json
{
  "formatVersion": "1",
  "registry-url": "https://fhir.github.io/ig-registry/tx-servers.json",
  "authoritative": [
    {
      "server-name": "SNOMED International",
      "url": "https://snowstorm.snomed.org/fhir",
      "fhirVersion": "4.0.1",
      "open": true,
      "access_info": "Publicly accessible"
    }
  ],
  "candidate": [
    {
      "server-name": "HL7 Terminology Server",
      "url": "http://tx.fhir.org/r4",
      "fhirVersion": "4.0.1",
      "open": true,
      "content": "complete"
    }
  ]
}
```

**Resolution response fields:**

Root object:

| Field | Type | Description |
|-------|------|-------------|
| `formatVersion` | String | Response format version (currently `1`) |
| `registry-url` | String (URL) | Master registry URL |
| `authoritative` | Array of ResolvedServer | Authoritative servers |
| `candidate` | Array of ResolvedServer | Candidate servers |

ResolvedServer object:

| Field | Type | Description |
|-------|------|-------------|
| `server-name` | String | Human-readable server name |
| `url` | String (URL) | FHIR endpoint URL |
| `fhirVersion` | String | FHIR version (semver) |
| `open`, `password`, `token`, `oauth`, `smart`, `cert` | Boolean | Supported authentication methods |
| `access_info` | String | Description of how to access (if not open) |
| `content` | String | Content level for candidates (not-present, example, fragment, complete, supplement) |

### Common code system URLs

| Code system | URL |
|-------------|-----|
| SNOMED CT | `http://snomed.info/sct` |
| LOINC | `http://loinc.org` |
| RxNorm | `http://www.nlm.nih.gov/research/umls/rxnorm` |
| ICD-10 | `http://hl7.org/fhir/sid/icd-10` |
| UCUM | `http://unitsofmeasure.org` |

### Integration examples

```javascript
// JavaScript — resolve the authoritative SNOMED server
async function findSnomedServer() {
  const response = await fetch('/tx-reg/resolve?fhirVersion=R4&url=http://snomed.info/sct');
  const data = await response.json();
  return data.authoritative[0].url;
}
```

```python
# Python
import requests

def find_server(code_system):
    response = requests.get(
        'https://dev.termx.org/tx-reg/resolve',
        params={'fhirVersion': 'R4', 'url': code_system}
    )
    return response.json()['authoritative'][0]['url']
```

```java
// Java
String apiUrl = "https://dev.termx.org/tx-reg/resolve?fhirVersion=R4&url=http://snomed.info/sct";
HttpResponse<String> response = httpClient.send(
    HttpRequest.newBuilder().uri(URI.create(apiUrl)).build(),
    HttpResponse.BodyHandlers.ofString());
JsonNode result = objectMapper.readTree(response.body());
String serverUrl = result.get("authoritative").get(0).get("url").asText();
```

```bash
# Shell — end-to-end: resolve, then use the server
RESOLVE=$(curl -s "https://dev.termx.org/tx-reg/resolve?fhirVersion=R4&url=http://snomed.info/sct")
SERVER_URL=$(echo "$RESOLVE" | jq -r '.authoritative[0].url')
curl "$SERVER_URL/CodeSystem/\$lookup?system=http://snomed.info/sct&code=38341003"
```

For an existing `tx.fhir.org/tx-reg` consumer, TermX is a drop-in replacement — same query parameters and response format; only the host changes.

### Web UI (`/tx-ecosystem/`)

An interactive single-page UI for exploring the ecosystem, served at:

```
https://dev.termx.org/tx-ecosystem/      # production
http://localhost:8200/tx-ecosystem/      # local
```

Publicly accessible, no login. It is a self-contained HTML file (HTML5 + CSS3 + vanilla ES6 JavaScript, Fetch API) with no external dependencies or frameworks. All API calls use relative URLs (`/tx-reg`), so it works under any host or domain automatically.

**Discovery tab** — list and filter servers:

- Filters: FHIR version, registry code, server code, CodeSystem URL, and an "authoritative only" toggle.
- Actions: Discover Servers, Clear Filters, Download Results (JSON).
- Results table: server name and code, clickable endpoint URL, FHIR version, number of CodeSystems, access methods (Open, OAuth, Token, SMART), and authoritative CodeSystems.

**Resolution tab** — find the server for a specific terminology:

- Required: FHIR version, and one of CodeSystem URL or ValueSet URL (client-side validated).
- Optional: usage type (publication, validation, code-generation), "authoritative only" toggle.
- Results: authoritative servers and candidate servers shown in separate sections; candidates additionally show their content level.

Under the hood the UI issues one HTTP request per action, e.g.:

```javascript
fetch('/tx-reg?fhirVersion=R4&authoritativeOnly=true').then(r => r.json());
fetch('/tx-reg/resolve?fhirVersion=R4&url=http://snomed.info/sct').then(r => r.json());
window.location.href = '/tx-reg?fhirVersion=R4&download=true'; // download
```

The API base is a single `API_BASE = '/tx-reg'` constant in the page if customization is ever needed.

**Serving:** the file lives at `termx-app/src/main/resources/static/tx-ecosystem/index.html` and is exposed via the static-resource mapping and public-endpoint entry shown in Deployment. If `/tx-ecosystem/` returns 404, confirm the static-resource mapping, that the `index.html` exists, and re-run `./gradlew :termx-app:processResources`.

### Architecture

The API is a transparent proxy: the controller binds and validates query parameters, the service builds the upstream query string and executes an HTTP GET, and the raw JSON response is returned to the client without transformation. Returning the response as-is keeps TermX compliant with the IG and avoids drift when the coordination server API changes.

```
Client --GET /tx-reg[/resolve]--> TerminologyEcosystemController
        --> TerminologyEcosystemService --HTTP GET--> Coordination Server (tx.fhir.org/tx-reg)
        <-- JSON (unchanged) <--------------------------
```

- **TerminologyEcosystemController** — public REST endpoints at `/tx-reg`; binds/validates query params; adds a `Content-Disposition` (attachment) header when `download=true`.
- **TerminologyEcosystemService** — holds the configured coordination-server URL and an HTTP client; builds the query string, executes the GET, validates required parameters (400), maps connection failures to 502, and returns a `JsonNode` with no transformation.
- **HTTP client** — reuses `com.kodality.commons.client.HttpClient` (Java 11+ HTTP client); no authentication toward the coordination server.

**Error handling:** invalid parameters → 400; coordination server unreachable or erroring → 502 (logged with detail); otherwise the upstream JSON is passed through.

**Source files:**

| File | Purpose |
|------|---------|
| `termx-core/src/main/java/org/termx/core/sys/server/TerminologyEcosystemController.java` | REST controller: `/tx-reg` endpoints |
| `termx-core/src/main/java/org/termx/core/sys/server/TerminologyEcosystemService.java` | Service: HTTP client to the coordination server |
| `termx-app/src/main/resources/static/tx-ecosystem/index.html` | Static web UI |
| `termx-app/src/main/resources/application.yml` | Coordination-server URL, public endpoints, static-resource mapping |

The controller endpoints are also surfaced in the Micronaut-generated OpenAPI / Swagger UI at `/swagger`.

### Testing

Integration tests live in `termx-integtest/src/test/groovy/org/termx/core/TerminologyEcosystemTest.groovy` and require Docker (Testcontainers provides the embedded PostgreSQL).

```bash
./gradlew :termx-integtest:test --tests "TerminologyEcosystemTest"
```

Coverage includes discovery and resolution accessibility and response structure, all discovery filters, required-parameter validation (400), download headers (`Content-Disposition`), `application/json` content type, public access (no auth), coordination-server-unavailable handling (502), URL encoding, all FHIR versions, and usage tokens.

Quick manual checks:

```bash
# Structure and non-empty results
curl https://dev.termx.org/tx-reg | jq '.results[0] | keys'
curl https://dev.termx.org/tx-reg | jq '.results | length'

# Resolution returns an authoritative server
curl "https://dev.termx.org/tx-reg/resolve?fhirVersion=R4&url=http://snomed.info/sct" | jq '.authoritative | length'

# Missing required parameter -> HTTP 400
curl -w "\nHTTP %{http_code}\n" "https://dev.termx.org/tx-reg/resolve?url=http://snomed.info/sct"

# Download headers
curl -I "https://dev.termx.org/tx-reg?download=true" | grep -i content-disposition
```

### External resources

- HL7 FHIR Terminology Ecosystem IG — https://build.fhir.org/ig/HL7/fhir-tx-ecosystem-ig/
- HL7 coordination server — http://tx.fhir.org/tx-reg/
- FHIR documentation — https://www.hl7.org/fhir/
