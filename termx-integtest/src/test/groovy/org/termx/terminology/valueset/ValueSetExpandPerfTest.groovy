package org.termx.terminology.valueset

import com.kodality.commons.model.LocalizedName
import com.kodality.commons.util.JsonUtil
import com.kodality.zmei.fhir.FhirMapper
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import org.termx.TermxIntegTest
import org.termx.core.auth.SessionInfo
import org.termx.core.auth.SessionStore
import org.termx.terminology.fhir.codesystem.CodeSystemFhirMapper
import org.termx.terminology.terminology.codesystem.CodeSystemImportService
import org.termx.terminology.terminology.valueset.ValueSetService
import org.termx.terminology.terminology.valueset.expansion.ValueSetVersionConceptRepository
import org.termx.terminology.terminology.valueset.expansion.ValueSetVersionConceptService
import org.termx.terminology.terminology.valueset.version.ValueSetVersionService
import org.termx.ts.PublicationStatus
import org.termx.ts.codesystem.CodeSystemImportAction
import org.termx.ts.property.PropertyReference
import org.termx.ts.valueset.ValueSet
import org.termx.ts.valueset.ValueSetTransactionRequest
import org.termx.ts.valueset.ValueSetVersion
import org.termx.ts.valueset.ValueSetVersionRuleSet
import org.termx.ts.valueset.ValueSetVersionRuleSet.ValueSetVersionRule
import org.termx.ts.valueset.ValueSetVersionRuleSet.ValueSetVersionRule.ValueSetRuleFilter
import spock.lang.IgnoreIf

/**
 * Expansion benchmark for a large is-a hierarchy — compares the two SQL expansion engines on the
 * same data: {@code value_set_expand(bigint)} (stored path, fn 01) vs {@code value_set_expand(text)}
 * (inline path, fn 37), and the cached-snapshot read used by published {@code $expand}.
 *
 * <p>Gated behind {@code PERF} so CI skips it; size via {@code PERF_N} (concepts) and
 * {@code PERF_B} (branching factor, default 4):
 * <pre>
 *   ./gradlew :termx-integtest:test --tests '*ValueSetExpandPerfTest' PERF=true PERF_N=20000
 * </pre>
 * Prints {@code PERF ...} lines; assertions only sanity-check that the hierarchy expanded fully.
 */
@MicronautTest(transactional = true)
@IgnoreIf({ env.PERF == null })
class ValueSetExpandPerfTest extends TermxIntegTest {
  @Inject CodeSystemImportService importService
  @Inject CodeSystemFhirMapper fhirMapper
  @Inject ValueSetService valueSetService
  @Inject ValueSetVersionService valueSetVersionService
  @Inject ValueSetVersionConceptRepository conceptRepository
  @Inject ValueSetVersionConceptService conceptService

  static final String CS_ID = "perf-hier-cs"
  static final String CS_URI = "http://termx-test.local/CodeSystem/perf-hier-cs"
  static final String VS_ID = "perf-hier-vs"
  static final String VS_VERSION = "1.0.0"

  void setup() {
    def sessionInfo = new SessionInfo()
    sessionInfo.privileges = ['*.*.*']
    SessionStore.setLocal(sessionInfo)
  }

  void cleanup() {
    SessionStore.clearLocal()
  }

  def "is-a expansion of an N-concept hierarchy: stored fn vs inline fn vs cached snapshot"() {
    given:
    int n = (System.getenv('PERF_N') ?: '10000') as int
    int b = (System.getenv('PERF_B') ?: '4') as int
    importHierarchy(n, b)

    and: "a draft value set whose single rule is is-a over the root concept c1"
    def rule = new ValueSetVersionRule().setType("include").setCodeSystem(CS_ID)
        .setFilters([new ValueSetRuleFilter().setProperty(new PropertyReference().setName("concept")).setOperator("is-a").setValue("c1")])
    saveDraftValueSet(rule)
    def versionId = valueSetVersionService.load(VS_ID, VS_VERSION).orElseThrow().getId()

    when: "stored path — value_set_expand(bigint), fn 01"
    long storedCount = -1
    long storedMs = median(3) { storedCount = conceptRepository.expand(versionId).size() }

    and: "inline path — value_set_expand(text), fn 37"
    def inlineJson = JsonUtil.toJson([resourceType: "ValueSet", compose: [inactive: false,
        include: [[system: CS_URI, version: VS_VERSION, filter: [[property: "concept", op: "is-a", value: "c1"]]]]]])
    long inlineCount = -1
    long inlineMs = median(3) { inlineCount = conceptRepository.expandFromJson(inlineJson).size() }

    and: "cached snapshot read — what published \$expand serves"
    conceptService.expand(VS_ID, VS_VERSION)            // materialize + persist snapshot once
    valueSetVersionService.activate(VS_ID, VS_VERSION)  // freeze so the snapshot is served, not recomputed
    long snapCount = -1
    long snapMs = median(5) { snapCount = conceptService.expand(VS_ID, VS_VERSION, null, false).getExpansion().size() }

    then:
    storedCount == n
    inlineCount == n
    snapCount == n
    println "PERF  n=${n} branching=${b}  storedFn01=${storedMs}ms  inlineFn37=${inlineMs}ms  cachedSnapshot=${snapMs}ms  (count=${n})"
    true
  }

  // --- helpers ---------------------------------------------------------------

  private long median(int reps, Closure body) {
    def times = (1..reps).collect { long t = System.nanoTime(); body(); (System.nanoTime() - t) / 1_000_000L as long }.sort()
    return times[(int) (times.size() / 2)]
  }

  private void importHierarchy(int n, int b) {
    Map<Integer, List<Integer>> children = [:].withDefault { [] }
    for (int i = 2; i <= n; i++) {
      int parent = ((i - 2).intdiv(b)) + 1
      children[parent] << i
    }
    Closure<Map> buildConcept
    buildConcept = { int i ->
      def c = [code: "c${i}".toString(), display: "Concept ${i}".toString()]
      def kids = children[i]
      if (kids) {
        c.concept = kids.collect { buildConcept(it) }
      }
      return c
    }
    def cs = [resourceType: "CodeSystem", id: CS_ID, url: CS_URI, version: "1.0.0", name: "PerfHierCs",
              title: "Perf hierarchy code system",
              status: "active", content: "complete", caseSensitive: true, hierarchyMeaning: "is-a",
              concept: [buildConcept(1)]]
    def fhir = FhirMapper.fromJson(JsonUtil.toJson(cs), com.kodality.zmei.fhir.resource.terminology.CodeSystem)
    importService.importCodeSystem(fhirMapper.fromFhirCodeSystem(fhir), [],
        new CodeSystemImportAction().setActivate(true).setCleanRun(true).setCleanConceptRun(false))
  }

  private void saveDraftValueSet(ValueSetVersionRule rule) {
    def version = new ValueSetVersion()
        .setStatus(PublicationStatus.draft)
        .setRuleSet(new ValueSetVersionRuleSet().setRules([rule]))
    version.setValueSet(VS_ID)
    version.setVersion(VS_VERSION)
    def request = new ValueSetTransactionRequest()
    request.setValueSet(new ValueSet().setId(VS_ID).setUri("http://termx-test.local/ValueSet/" + VS_ID)
        .setTitle(new LocalizedName().add("en", "Perf hierarchy")))
    request.setVersion(version)
    valueSetService.save(request)
  }
}
