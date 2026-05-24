package org.termx.terminology.fhir

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import org.termx.TermxIntegTest
import org.termx.core.auth.SessionInfo
import org.termx.core.auth.SessionStore
import org.termx.terminology.fhir.codesystem.CodeSystemFhirImportService
import org.termx.terminology.fhir.codesystem.CodeSystemResourceStorage
import org.termx.terminology.fhir.valueset.ValueSetFhirImportService
import org.termx.terminology.fhir.valueset.ValueSetResourceStorage
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject

/**
 * End-to-end FHIR round-trip test driven by three coordinated resources.
 *
 * Fixtures (under {@code fhir/round-trip/}):
 * <ul>
 *   <li>{@code cs1.json} — CodeSystem with all seven FHIR datatype properties
 *       (code, Coding, string, integer, boolean, dateTime, decimal). One concept
 *       ("b") carries multiple {@code p-string} occurrences AND a per-value
 *       extension cluster — the latter is the gap this PR closes.</li>
 *   <li>{@code vs1.json} — ValueSet enumerating two concepts from CS1.</li>
 *   <li>{@code cs2.json} — CodeSystem with two Coding properties whose
 *       {@code CodeSystemProperty.extension[]} bind to CS1 and VS1
 *       respectively (the {@code codesystem-property-codesystem} and
 *       {@code codesystem-property-valueset} extensions that already round-trip).</li>
 * </ul>
 *
 * Contract: import all three, fetch each back through the same code path the
 * FHIR API uses ({@link CodeSystemResourceStorage#load} /
 * {@link ValueSetResourceStorage#load}), normalise out server-set fields, and
 * assert the normalised JSON equals the input. The "FHIR API" reads via these
 * resource-storage loaders — same code path the HTTP controller delegates to,
 * but without the Netty round-trip; equivalence is the same.
 *
 * Expected state at PR open: assertions on the property-definition binding
 * extensions (CS2) pass. The per-value extension assertion on CS1.b pinpoints
 * the bug to fix.
 */
@MicronautTest(transactional = true)
class CodeSystemRoundTripTest extends TermxIntegTest {
  @Inject CodeSystemFhirImportService csImportService
  @Inject ValueSetFhirImportService vsImportService
  @Inject CodeSystemResourceStorage csStorage
  @Inject ValueSetResourceStorage vsStorage

  static final ObjectMapper MAPPER = new ObjectMapper()

  void setup() {
    def sessionInfo = new SessionInfo()
    sessionInfo.privileges = ['*.*.*']
    SessionStore.setLocal(sessionInfo)
  }

  void cleanup() {
    SessionStore.clearLocal()
  }

  def "import CS1 + VS1 + CS2, then re-fetch via FHIR API; payload round-trips"() {
    given: "the coordinated fixtures (CS0 is a tiny preamble so CS1's Coding-typed property values can resolve to a stored Concept)"
    def cs0Input = readFixture("round-trip/cs0.json")
    def cs1Input = readFixture("round-trip/cs1.json")
    def vs1Input = readFixture("round-trip/vs1.json")
    def cs2Input = readFixture("round-trip/cs2.json")

    when: "imported in dependency order (CS0 → CS1 → VS1 → CS2)"
    csImportService.importCodeSystem(cs0Input.toString(), cs0Input.get("id").asText())
    csImportService.importCodeSystem(cs1Input.toString(), cs1Input.get("id").asText())
    vsImportService.importValueSet(vs1Input.toString(), vs1Input.get("id").asText())
    csImportService.importCodeSystem(cs2Input.toString(), cs2Input.get("id").asText())

    and: "fetched back via the same code path the FHIR API GET uses"
    def cs1Output = readBack(csStorage, cs1Input.get("id").asText(), cs1Input.get("version").asText())
    def vs1Output = readBack(vsStorage, vs1Input.get("id").asText(), vs1Input.get("version").asText())
    def cs2Output = readBack(csStorage, cs2Input.get("id").asText(), cs2Input.get("version").asText())

    then: "CS1: top-level identity survives"
    def cs1In = normalise(cs1Input)
    def cs1Out = normalise(cs1Output)
    cs1Out.get("url")     == cs1In.get("url")
    cs1Out.get("version") == cs1In.get("version")
    cs1Out.get("name")    == cs1In.get("name")
    cs1Out.get("status")  == cs1In.get("status")

    and: "CS1 property definitions round-trip"
    propertyDefs(cs1Out) == propertyDefs(cs1In)

    and: "CS1 concept `a` keeps all seven FHIR datatype property values"
    // p-datetime tests full timestamp precision (12:30:45Z) — not midnight.
    // p-coding here points to a *stored* CS (rt-cs0) so resolution succeeds;
    // the user-supplied `display` must still come back on the round-trip.
    conceptProps(cs1Out, "a") == conceptProps(cs1In, "a")

    and: "CS1 concept `c` keeps Coding values whose system is NOT a stored CodeSystem"
    // Tests the silent-drop bug: import sees an unresolvable Coding (the URL
    // isn't a CS we know about), stores it, and the export must still emit it
    // verbatim — not silently drop it because Concept-style field lookup fails.
    conceptProps(cs1Out, "c") == conceptProps(cs1In, "c")

    and: "CS1 concept `b` keeps repeated p-string values AND the per-value extension cluster"
    // The repetition itself ("first" / "second" / pipe-string) is preserved
    // by the existing repository. The extension[] sibling on the pipe-string
    // entry is what PR #152 added — still green here.
    conceptProps(cs1Out, "b") == conceptProps(cs1In, "b")

    and: "CS2: property-definition binding extensions (codesystem-property-codesystem / -valueset) round-trip"
    def cs2In = normalise(cs2Input)
    def cs2Out = normalise(cs2Output)
    propertyDefs(cs2Out) == propertyDefs(cs2In)

    and: "CS2 per-concept Coding values keep system + code + display"
    // The `display` round-trip on Coding-typed property values exercises the
    // fix for the import-time normalization that previously discarded display
    // when Coding successfully resolved to a stored TermX Concept.
    conceptProps(cs2Out, "x") == conceptProps(cs2In, "x")
    conceptProps(cs2Out, "y") == conceptProps(cs2In, "y")

    and: "VS1: identity + compose.include[] survive (tolerate server-added defaults like compose.inactive)"
    def vs1In = normalise(vs1Input)
    def vs1Out = normalise(vs1Output)
    vs1Out.get("url")     == vs1In.get("url")
    vs1Out.get("version") == vs1In.get("version")
    vs1Out.get("compose").get("include") == vs1In.get("compose").get("include")
  }


  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  /** Read a fixture from src/test/resources/fhir/&lt;path&gt; into a mutable JsonNode tree. */
  private JsonNode readFixture(String relativePath) {
    def stream = getClass().getClassLoader().getResourceAsStream("fhir/${relativePath}")
    assert stream != null, "fixture not found: fhir/${relativePath}"
    return MAPPER.readTree(stream)
  }

  /**
   * Re-fetch a resource through the FHIR resource-storage layer (the same code
   * path the FHIR HTTP GET controller delegates to) and parse to JsonNode.
   */
  private JsonNode readBack(Object storage, String id, String version) {
    def resourceVersion = storage.load(id + "--" + version)
    assert resourceVersion != null, "resource not found after import: ${id}--${version}"
    return MAPPER.readTree(resourceVersion.content.value as String)
  }

  /**
   * Drop server-set or computed fields that are expected to differ between
   * input and round-tripped output — these are not part of the round-trip
   * contract and would otherwise mask real divergences.
   *
   * Removed:
   * <ul>
   *   <li>{@code id} — the read endpoint uses a composite "id--version" form.</li>
   *   <li>{@code date}, {@code meta} — server-set on every read.</li>
   *   <li>{@code count} — derived from concept count, not a round-trip input.</li>
   *   <li>{@code text} — narrative; not exercised here.</li>
   * </ul>
   */
  private static JsonNode normalise(JsonNode node) {
    ObjectNode copy = node.deepCopy() as ObjectNode
    ["id", "date", "meta", "count", "text"].each { copy.remove(it) }
    return copy
  }

  /** Extract a stable comparison view of `property[]` (definitions) — sort by code. */
  private static JsonNode propertyDefs(JsonNode codeSystem) {
    ArrayNode props = codeSystem.get("property") as ArrayNode
    if (props == null) {
      return MAPPER.createArrayNode()
    }
    def sorted = MAPPER.createArrayNode()
    props.toList().sort { it.get("code").asText() }.each { sorted.add(it) }
    return sorted
  }

  /**
   * Extract the `concept[].property[]` list for a given concept code, with
   * canonical key ordering recursively applied and array elements sorted by
   * their canonical-string form. Both normalisations are necessary because:
   *
   * <ul>
   *   <li>The export side alphabetises property order
   *       (see {@code toFhirConceptProperties.sort}), so input and output
   *       sequences differ unless we sort.</li>
   *   <li>FHIR POJO serialisation emits object fields in declaration order
   *       (e.g. {@code extension, code, valueString}) while JSON input
   *       preserves source order (e.g. {@code code, valueString, extension}).
   *       Comparing JsonNode-via-{@code equals} is order-insensitive for
   *       fields but Groovy's {@code ==} on Jackson nodes can be sensitive in
   *       some cases; recursive key-canonicalisation makes the comparison
   *       robust regardless.</li>
   * </ul>
   *
   * Returns an empty array when the concept is missing or has no properties.
   * NOTE: cannot use Groovy elvis ({@code ?:}) on JsonNode — its
   * {@code asBoolean()} returns false for non-boolean nodes, so a populated
   * array would be "falsy" to Groovy.
   */
  private static JsonNode conceptProps(JsonNode codeSystem, String code) {
    def concept = findConcept(codeSystem.get("concept"), code)
    if (concept == null || concept.get("property") == null) {
      return MAPPER.createArrayNode()
    }
    def props = (concept.get("property") as ArrayNode).toList().collect { canonicalise(it) }
    def sorted = MAPPER.createArrayNode()
    props.sort { a, b -> a.toString() <=> b.toString() }.each { sorted.add(it) }
    return sorted
  }

  /** Recursively sort all ObjectNode field keys alphabetically; preserves array order. */
  private static JsonNode canonicalise(JsonNode node) {
    if (node instanceof ObjectNode) {
      ObjectNode out = MAPPER.createObjectNode()
      def fields = []
      node.fieldNames().forEachRemaining { fields << it }
      fields.sort().each { fname -> out.set(fname, canonicalise(node.get(fname))) }
      return out
    }
    if (node instanceof ArrayNode) {
      ArrayNode out = MAPPER.createArrayNode()
      node.forEach { out.add(canonicalise(it)) }
      return out
    }
    return node
  }

  /** Depth-first concept lookup by code — concepts may be nested. */
  private static JsonNode findConcept(JsonNode concepts, String code) {
    if (concepts == null || !concepts.isArray()) {
      return null
    }
    for (def c : concepts) {
      if (c.get("code")?.asText() == code) {
        return c
      }
      def nested = findConcept(c.get("concept"), code)
      if (nested != null) {
        return nested
      }
    }
    return null
  }
}
