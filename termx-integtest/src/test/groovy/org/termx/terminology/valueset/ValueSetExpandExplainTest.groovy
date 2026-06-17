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

import javax.sql.DataSource

/**
 * EXPLAIN (ANALYZE, BUFFERS) of is-a expansion over a large hierarchy, for both engines — to locate
 * the dominant cost behind the superlinear scaling seen in {@link ValueSetExpandPerfTest}.
 *
 * <p>Runs transactional and reuses the transaction-bound connection for the EXPLAIN so it sees the
 * data imported in the same transaction. Gated behind {@code PERF}; size via {@code PERF_N} /
 * {@code PERF_B}:
 * <pre>
 *   ./gradlew :termx-integtest:test --tests '*ValueSetExpandExplainTest' PERF=true PERF_N=20000
 * </pre>
 */
@MicronautTest(transactional = true)
@IgnoreIf({ env.PERF == null })
class ValueSetExpandExplainTest extends TermxIntegTest {
  @Inject CodeSystemImportService importService
  @Inject CodeSystemFhirMapper fhirMapper
  @Inject ValueSetService valueSetService
  @Inject ValueSetVersionService valueSetVersionService
  @Inject DataSource dataSource

  static final String CS_ID = "explain-hier-cs"
  static final String CS_URI = "http://termx-test.local/CodeSystem/explain-hier-cs"
  static final String VS_ID = "explain-hier-vs"
  static final String VS_VERSION = "1.0.0"

  void setup() {
    def sessionInfo = new SessionInfo()
    sessionInfo.privileges = ['*.*.*']
    SessionStore.setLocal(sessionInfo)
  }

  void cleanup() {
    SessionStore.clearLocal()
  }

  def "EXPLAIN ANALYZE is-a expansion (stored fn01 + inline fn37)"() {
    given:
    int n = (System.getenv('PERF_N') ?: '20000') as int
    int b = (System.getenv('PERF_B') ?: '6') as int
    importHierarchy(n, b)
    def rule = new ValueSetVersionRule().setType("include").setCodeSystem(CS_ID)
        .setFilters([new ValueSetRuleFilter().setProperty(new PropertyReference().setName("concept")).setOperator("is-a").setValue("c1")])
    saveDraftValueSet(rule)
    def versionId = valueSetVersionService.load(VS_ID, VS_VERSION).orElseThrow().getId()
    def inlineJson = JsonUtil.toJson([resourceType: "ValueSet", compose: [inactive: false,
        include: [[system: CS_URI, version: VS_VERSION, filter: [[property: "concept", op: "is-a", value: "c1"]]]]]])

    when:
    def storedPlan = explain("select * from terminology.value_set_expand(${versionId}::bigint)", null)
    // EXPLAIN the function BODY directly (param inlined) so the inner CTE node tree is visible — the
    // function-scan EXPLAIN above only shows the opaque outer call.
    def body = fetchBigintBody().replace("p_value_set_version_id", versionId.toString())
    def innerPlan = explain(body, null)

    then:
    println "\n===================== EXPLAIN stored value_set_expand(bigint)  n=${n} b=${b} =====================\n" + storedPlan
    println "\n===================== EXPLAIN INNER body of value_set_expand(bigint)  n=${n} b=${b} =====================\n" + innerPlan
    true
  }

  private String fetchBigintBody() {
    def conn = org.springframework.jdbc.datasource.DataSourceUtils.getConnection(dataSource)
    try {
      def rs = conn.createStatement().executeQuery(
          "select prosrc from pg_proc p where proname = 'value_set_expand' and pg_get_function_arguments(p.oid) like '%bigint%'")
      rs.next()
      return rs.getString(1)
    } finally {
      org.springframework.jdbc.datasource.DataSourceUtils.releaseConnection(conn, dataSource)
    }
  }

  private String explain(String sql, String textParam) {
    // Reuse the connection bound to the test's transaction so the EXPLAIN sees the data imported in
    // the same transaction (and so Micronaut's connection management is satisfied).
    def conn = org.springframework.jdbc.datasource.DataSourceUtils.getConnection(dataSource)
    try {
      def full = "EXPLAIN (ANALYZE, BUFFERS, VERBOSE) " + sql
      def rs
      if (textParam == null) {
        rs = conn.createStatement().executeQuery(full)
      } else {
        def ps = conn.prepareStatement(full)
        ps.setString(1, textParam)
        rs = ps.executeQuery()
      }
      def sb = new StringBuilder()
      while (rs.next()) {
        sb.append(rs.getString(1)).append("\n")
      }
      return sb.toString()
    } finally {
      org.springframework.jdbc.datasource.DataSourceUtils.releaseConnection(conn, dataSource)
    }
  }

  private void importHierarchy(int n, int b) {
    Map<Integer, List<Integer>> children = [:].withDefault { [] }
    for (int i = 2; i <= n; i++) {
      children[((i - 2).intdiv(b)) + 1] << i
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
    def cs = [resourceType: "CodeSystem", id: CS_ID, url: CS_URI, version: "1.0.0", name: "ExplainHierCs",
              title: "Explain hierarchy code system", status: "active", content: "complete",
              caseSensitive: true, hierarchyMeaning: "is-a", concept: [buildConcept(1)]]
    def fhir = FhirMapper.fromJson(JsonUtil.toJson(cs), com.kodality.zmei.fhir.resource.terminology.CodeSystem)
    importService.importCodeSystem(fhirMapper.fromFhirCodeSystem(fhir), [],
        new CodeSystemImportAction().setActivate(true).setCleanRun(true).setCleanConceptRun(false))
  }

  private void saveDraftValueSet(ValueSetVersionRule rule) {
    def version = new ValueSetVersion().setStatus(PublicationStatus.draft)
        .setRuleSet(new ValueSetVersionRuleSet().setRules([rule]))
    version.setValueSet(VS_ID)
    version.setVersion(VS_VERSION)
    def request = new ValueSetTransactionRequest()
    request.setValueSet(new ValueSet().setId(VS_ID).setUri("http://termx-test.local/ValueSet/" + VS_ID)
        .setTitle(new LocalizedName().add("en", "Explain hierarchy")))
    request.setVersion(version)
    valueSetService.save(request)
  }
}
