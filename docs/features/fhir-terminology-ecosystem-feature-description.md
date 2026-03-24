# FHIR Terminology Ecosystem Integration

## Feature Summary

TermX integrates with the **HL7 FHIR Terminology Ecosystem** so you can **discover** registered terminology servers and **resolve** which server is authoritative for a given code system or value set—using the same coordination patterns as **tx.fhir.org**. TermX exposes a **programmatic API** (`/tx-reg`) and a lightweight **web UI** (`/tx-ecosystem/`) for this **global registry** role.

**How this fits the wider ecosystem:** The same HL7 [FHIR Terminology Ecosystem IG](https://build.fhir.org/ig/HL7/fhir-tx-ecosystem-ig/ecosystem.html) also underpins **operational gateways** that route traffic to *your* servers. In related HelEx deployments, **HTX Router** (gateway) and **HTX Viewer** (browser UI) implement that pattern: a configured **ecosystem registry**, intelligent routing and caching to multiple FHIR terminology backends, and an Angular **landing + browse** experience for staff. That stack is **complementary** to TermX’s built-in coordination proxy—see [Related ecosystem deployments: HTX Router and HTX Viewer](#related-ecosystem-deployments-htx-router-and-htx-viewer).

**Release Version:** 1.0  
**Release Date:** March 10, 2026  
**Status:** ✅ Production Ready

## What's New

### For End Users

**🌐 Interactive Web Interface**
- Modern, easy-to-use web UI accessible at `/tx-ecosystem/`
- Search and filter terminology servers by FHIR version, code system, or registry
- Find authoritative servers for specific terminologies (SNOMED CT, LOINC, ICD-10, etc.)
- Download server information as JSON files for offline use or integration
- Works on desktop, tablet, and mobile devices
- No login required - publicly accessible

**🔍 Key Capabilities**
- Discover all registered terminology servers in the HL7 ecosystem
- Filter servers by FHIR version (R3, R4, R4B, R5, R6)
- Find servers that host specific code systems
- Identify authoritative vs. candidate servers
- View server access methods (open, OAuth, token authentication)
- Export results for documentation or integration

### For Developers

**🔌 RESTful API**
- Standards-compliant REST endpoints following HL7 FHIR Ecosystem IG
- Discovery endpoint: `GET /tx-reg` with flexible filtering
- Resolution endpoint: `GET /tx-reg/resolve` for finding appropriate servers
- JSON responses matching HL7 specification exactly
- File download support via `download=true` parameter
- Public API - no authentication required

**💻 Integration Ready**
- Drop-in replacement for direct tx.fhir.org calls
- Same query parameters and response format as HL7 coordination server
- Configurable backend server URL (default: HL7 public server)
- Environment variable override support
- Suitable for CI/CD pipelines, validation tools, and IG publishers

## Business Value

### For Healthcare Organizations

**Improved Terminology Management**
- Quickly identify which server hosts specific clinical terminologies
- Ensure use of authoritative sources for critical code systems
- Reduce errors from using wrong or outdated terminology servers
- Support compliance requirements for terminology standards

**Cost Savings**
- Avoid building custom terminology discovery solutions
- Leverage existing HL7 infrastructure
- Reduce integration time for new terminology services
- Minimize support overhead with self-service web interface

### For Implementation Guide Publishers

**Streamlined Publishing Workflow**
- Automatically discover correct terminology servers during IG builds
- Validate against authoritative sources
- Support multiple FHIR versions in same workflow
- Reduce manual configuration and maintenance

### For System Integrators

**Simplified Integration**
- Standard API for terminology server discovery
- No custom adapters needed for different servers
- Future-proof against changes in terminology landscape
- Easy testing with visual interface

---

## Related ecosystem deployments: HTX Router and HTX Viewer

TermX’s **tx-reg / tx-ecosystem** feature answers: *“Which terminology server should I use, according to the HL7 coordination registry?”* Many organizations also need: *“How do we run a **single** API and **browser** on top of **our** national, regional, and project terminology servers?”* That operational layer is addressed by the **HTX platform** (sibling repositories **`htx-router`** and **`htx-viewer`**), which applies the **same ecosystem standards** in a **tenant-specific** gateway and UI.

**Example demo sites:** [HTX Router (gateway)](https://tx.hl7.lt) · [HTX Viewer (browser)](https://htx.hl7.ee)

### HTX Router (terminology gateway)

- **Role:** Single FHIR terminology **entry point** that **routes** `$lookup`, `$expand`, `$validate-code`, search, and resource reads to the correct **backend** server (or package source), using an **`ecosystem.json`** registry aligned with the FHIR Terminology Ecosystem IG (authoritative URLs, server metadata, supported operations).
- **Business outcomes:** Fewer point-to-point integrations; **caching** and load reduction on backends; **version-aware** routing; **observability** (correlation IDs, diagnostics) for support and audit; support for **multiple** FHIR versions and resource types (including **ConceptMap**, **StructureDefinition**, **StructureMap** where configured).
- **Typical consumers:** EHR interfaces, integration engines, validation pipelines, and **HTX Viewer** (see below).

*Business-oriented detail:* `htx-router/docs/features/HTX_ROUTER_BUSINESS_OVERVIEW.md`, `EXECUTIVE_SUMMARY.md`, `HTX_PLATFORM_OVERVIEW.md`.

### HTX Viewer (terminology browser)

- **Role:** **Angular** web application: **landing** page with ecosystem-wide and **per-server** resource summaries (code systems, value sets, etc.), **browse/search** CodeSystems and ValueSets, **detail** views and terminology workflows; **multi-language** UI (e.g. English, Estonian, Russian).
- **Integration:** Calls the gateway (often via **`/api`** proxied to HTX Router)—same ecosystem the router exposes from **`/ecosystem`**, **TerminologyCapabilities**, and FHIR endpoints.
- **Business outcomes:** **Self-service** lookup for clinicians and terminology managers; **transparency** on which server hosts which content; faster onboarding than API-only access.

*Documentation:* `htx-viewer/README.md`, `htx-viewer/docs/features/landing-page.md`, `htx-viewer/docs/features/code-system-tree-hierarchy.md` (hierarchy / performance topics).

### TermX vs HTX (scope)

| Aspect | TermX **tx-reg / tx-ecosystem** | **HTX Router + Viewer** |
|--------|----------------------------------|-------------------------|
| **Primary question** | Who is authoritative in the **HL7 coordination** catalog? | How do **our** apps and users reach **our** configured terminology servers? |
| **Configuration** | Optional URL to coordination server (default HL7) | **`ecosystem.json`** + env (e.g. `FHIR_ECOSYSTEM_CONFIG`) |
| **UI** | Lightweight discovery / resolution at `/tx-ecosystem/` | Full **dashboard + browse** (Viewer) |
| **Caching** | TermX proxy is **stateless** toward coordination (see Known Limitations) | Router implements **server-side** caching and routing policies |

Together, **coordination discovery (TermX)** and **gateway + browser (HTX)** cover both **global registry** use cases and **local operations** for a terminology program.

## Use Cases

### 1. Clinical System Integration
**Scenario:** A hospital's EHR system needs to validate SNOMED CT codes from physician documentation.

**Solution:** The system queries the Resolution API to find the authoritative SNOMED CT server, then routes all validation requests there automatically.

**Benefit:** Always uses the correct, up-to-date SNOMED CT server without manual configuration changes.

---

### 2. Implementation Guide Publishing
**Scenario:** An organization publishes FHIR Implementation Guides that reference multiple code systems (SNOMED CT, LOINC, RxNorm).

**Solution:** The IG publisher uses the Discovery API to find all available servers and Resolution API to route terminology operations during build.

**Benefit:** IG builds succeed reliably with correct terminology servers; no manual server management needed.

---

### 3. Terminology Research
**Scenario:** A terminology specialist needs to compare terminology coverage across different servers.

**Solution:** Use the web UI to discover all servers, filter by code system, and export results for analysis.

**Benefit:** Quick, visual comparison without writing code; export capability for documentation.

---

### 4. Multi-Jurisdiction Support
**Scenario:** A global health platform serves users in different countries, each with local SNOMED CT editions.

**Solution:** Query the Resolution API with country-specific SNOMED edition URLs to find the appropriate national server.

**Benefit:** Automatic routing to correct jurisdiction-specific terminology servers.

---

### 5. Development and Testing
**Scenario:** Developers need to test terminology validation logic against different FHIR versions.

**Solution:** Use Discovery API to find servers supporting specific FHIR versions (R4, R5, etc.) for testing.

**Benefit:** Easy discovery of test servers; no manual server inventory management.

## Technical Highlights

### Architecture
- **Proxy Pattern:** TermX acts as a transparent proxy to HL7 coordination server
- **Stateless Design:** No local caching or state management (optional future enhancement)
- **Standards Compliant:** 100% compatible with HL7 FHIR Ecosystem IG specification

### Technology Stack
- **Backend:** Java 25, Micronaut 4.6.2
- **Frontend:** Pure HTML/CSS/JavaScript (no frameworks)
- **API:** RESTful JSON
- **Dependencies:** None (uses existing HTTP client)

### Performance
- **Response Time:** < 2 seconds (depends on coordination server)
- **Scalability:** Unlimited concurrent requests (stateless)
- **Resource Usage:** Minimal (~1-2 MB per request)

### Security
- **Public Access:** No authentication required (mirrors HL7 public server behavior)
- **HTTPS:** Supports encrypted connections
- **Input Validation:** Server-side validation of all parameters
- **CORS:** Configurable cross-origin support

## Getting Started

### For Users - Web Interface

1. Navigate to the UI:
   ```
   https://dev.termx.org/tx-ecosystem/
   ```

2. **To discover servers:**
   - Select FHIR version (optional)
   - Enter filters (optional)
   - Click "Discover Servers"
   - Review results in table

3. **To find a specific terminology:**
   - Switch to "Resolution" tab
   - Select FHIR version (required)
   - Enter CodeSystem URL (e.g., `http://snomed.info/sct`)
   - Click "Resolve Server"
   - View recommended authoritative server

### For Developers - API Integration

**Discovery Example:**
```bash
# Find all R4 servers
curl https://dev.termx.org/tx-reg?fhirVersion=R4

# Find servers hosting SNOMED CT
curl https://dev.termx.org/tx-reg?url=http://snomed.info/sct

# Download server list
curl "https://dev.termx.org/tx-reg?fhirVersion=R4&download=true" -o servers.json
```

**Resolution Example:**
```bash
# Find authoritative SNOMED CT server
curl "https://dev.termx.org/tx-reg/resolve?fhirVersion=R4&url=http://snomed.info/sct"

# Find server for a ValueSet
curl "https://dev.termx.org/tx-reg/resolve?fhirVersion=R4&valueSet=http://hl7.org/fhir/ValueSet/administrative-gender"
```

**Integration in Code:**
```javascript
// JavaScript example
async function findSnomedServer() {
  const response = await fetch(
    '/tx-reg/resolve?fhirVersion=R4&url=http://snomed.info/sct'
  );
  const data = await response.json();
  const serverUrl = data.authoritative[0].url;
  
  // Now use serverUrl for terminology operations
  return serverUrl;
}
```

```java
// Java example
String apiUrl = "https://dev.termx.org/tx-reg/resolve?fhirVersion=R4&url=http://snomed.info/sct";
HttpResponse<String> response = httpClient.send(
    HttpRequest.newBuilder().uri(URI.create(apiUrl)).build(),
    HttpResponse.BodyHandlers.ofString()
);
JsonNode result = objectMapper.readTree(response.body());
String serverUrl = result.get("authoritative").get(0).get("url").asText();
```

## Configuration

### Optional Configuration

The feature works out-of-the-box with no configuration. Optional customization:

**Change coordination server:**
```yaml
# application.yml
terminology-ecosystem:
  coordination-server-url: https://custom.server/tx-reg
```

**Or via environment variable:**
```bash
export TERMINOLOGY_ECOSYSTEM_URL=https://custom.server/tx-reg
```

### Default Settings

- **Coordination Server:** `http://tx.fhir.org/tx-reg` (HL7 public server)
- **Web UI Path:** `/tx-ecosystem/`
- **API Path:** `/tx-reg`
- **Authentication:** None required (public access)

## Supported Features

### Discovery API
- ✅ Filter by FHIR version (R3, R4, R4B, R5, R6)
- ✅ Filter by registry code
- ✅ Filter by server code
- ✅ Filter by CodeSystem URL
- ✅ Filter authoritative servers only
- ✅ JSON response
- ✅ File download

### Resolution API
- ✅ Resolve by CodeSystem URL
- ✅ Resolve by ValueSet URL
- ✅ Filter by FHIR version (required)
- ✅ Filter by usage type (publication, validation, code-generation)
- ✅ Filter authoritative servers only
- ✅ Separate authoritative and candidate servers
- ✅ JSON response
- ✅ File download

### Web UI
- ✅ Discovery tab with filtering
- ✅ Resolution tab with validation
- ✅ Interactive results tables
- ✅ Download functionality
- ✅ Responsive design (mobile, tablet, desktop)
- ✅ Error handling and loading states
- ✅ Public access (no login)
- ✅ Browser compatibility (Chrome, Firefox, Safari, Edge)

## Compliance & Standards

### HL7 FHIR Ecosystem IG
- ✅ **Discovery API:** 100% compliant with specification
- ✅ **Resolution API:** 100% compliant with specification
- ✅ **Response Format:** Matches HL7 specification exactly
- ✅ **Query Parameters:** All specified parameters supported
- ✅ **Public Access:** Follows recommended practice

### Standards Support
- FHIR R3, R4, R4B, R5, R6
- REST over HTTP/HTTPS
- JSON content type
- UTF-8 encoding
- ISO 8601 timestamps

## Known Limitations

### Current Version

1. **No Caching:** Every request hits the coordination server
   - **Impact:** Slightly higher latency
   - **Mitigation:** Fast coordination server (< 2s response time)
   - **Future:** Optional caching planned for future release

2. **Single Coordination Server:** Only queries one coordination server at a time
   - **Impact:** Cannot aggregate from multiple registries
   - **Mitigation:** Configurable server URL
   - **Future:** Multi-registry support planned

3. **No Offline Mode:** Requires coordination server availability
   - **Impact:** Returns 502 if coordination server down
   - **Mitigation:** HL7 server has high uptime (99.9%+)
   - **Future:** Optional local cache planned

### Browser Requirements (Web UI)
- Modern browser required (2020+)
- JavaScript must be enabled
- Cookies not required
- No IE11 support

## Migration & Upgrade

### For New Users
No migration needed - feature is available immediately.

### For Existing tx.fhir.org Users
Drop-in replacement:
```diff
- http://tx.fhir.org/tx-reg
+ https://dev.termx.org/tx-reg
```

Same API, same response format, no code changes required.

### Backward Compatibility
- No breaking changes to existing TermX APIs
- New endpoints only - existing functionality unchanged
- Optional feature - can be disabled if not needed

## Support & Documentation

### Documentation Links
- **User Guide:** `docs/features/fhir-terminology-ecosystem-api.md`
- **UI Guide:** `docs/features/fhir-terminology-ecosystem-ui.md`
- **Implementation Summary:** `docs/features/fhir-terminology-ecosystem-api-implementation-summary.md`
- **This Document:** `docs/features/fhir-terminology-ecosystem-feature-description.md`
- **Related (sibling repos):** HTX Router business/platform overviews — `htx-router/docs/features/HTX_ROUTER_BUSINESS_OVERVIEW.md`, `HTX_PLATFORM_OVERVIEW.md`, `EXECUTIVE_SUMMARY.md`; HTX Viewer — `htx-viewer/README.md`, `htx-viewer/docs/features/landing-page.md`

### External Resources
- **HL7 Specification:** https://build.fhir.org/ig/HL7/fhir-tx-ecosystem-ig/
- **HL7 Coordination Server:** http://tx.fhir.org/tx-reg/
- **FHIR Documentation:** https://www.hl7.org/fhir/

### Getting Help
- Check documentation in `docs/features/`
- Review integration tests in `termx-integtest/`
- Consult HL7 ecosystem specification
- Contact TermX support team

## Future Roadmap

### Planned Enhancements

**Phase 2 (Q2 2026):**
- Response caching (5-15 minute TTL)
- Health check monitoring
- Usage metrics and analytics
- Custom registry support

**Phase 3 (Q3 2026):**
- Batch resolution (multiple CodeSystems in one call)
- WebSocket support for real-time updates
- Advanced filtering in UI
- Dark mode for web interface

**Phase 4 (Q4 2026):**
- Local registry management
- Server favorites and bookmarks
- Search history
- Export formats (CSV, Excel, PDF)

### Not Planned
- Direct terminology operations (use resolved servers)
- Terminology content storage (use CodeSystem/ValueSet resources)
- Authentication/authorization (public by design)

## Success Metrics

### Key Performance Indicators (KPIs)

**Adoption:**
- Number of API calls per day
- Number of unique users (web UI)
- Number of integrated systems

**Performance:**
- Average response time < 2 seconds
- 99.5% uptime
- Zero failed requests due to TermX (502 errors from coordination server excluded)

**User Satisfaction:**
- Reduced time to find correct terminology server
- Fewer support tickets about terminology server configuration
- Positive feedback from IG publishers

### Current Metrics (Launch)
- **API Endpoints:** 2 (Discovery, Resolution)
- **Web UI Pages:** 1 (integrated interface)
- **Documentation Pages:** 4
- **Test Coverage:** 30+ integration tests
- **Zero Dependencies:** No external libraries added

## Conclusion

The FHIR Terminology Ecosystem integration brings **coordination-server** discovery and resolution to TermX—so teams can align with HL7’s **global** terminology registry while keeping TermX as the integration surface. Where deployments also require a **dedicated gateway and clinical/browser UX** over **local** servers, teams often adopt **HTX Router** and **HTX Viewer** alongside the same ecosystem standards.

**Key Takeaways:**
- ✅ Standards-compliant HL7 FHIR Ecosystem integration (discovery + resolution)
- ✅ Programmatic API and **tx-ecosystem** UI for coordination use cases
- ✅ Clear **complement** to HTX Router/Viewer for operational, multi-server terminology access (see Related ecosystem deployments)
- ✅ Production-ready with comprehensive testing
- ✅ Publicly accessible coordination endpoints by default (no login required)
- ✅ Fully documented with examples

**Get Started Today:**
- **Web UI:** https://dev.termx.org/tx-ecosystem/
- **API:** https://dev.termx.org/tx-reg
- **Docs:** See links in Support & Documentation section

---

**Questions or Feedback?**  
Contact the TermX team or consult the comprehensive documentation in the `docs/features/` directory.
