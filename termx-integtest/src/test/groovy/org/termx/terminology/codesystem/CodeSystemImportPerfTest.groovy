package org.termx.terminology.codesystem

import com.kodality.zmei.fhir.FhirMapper
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import org.termx.TermxIntegTest
import org.termx.core.auth.SessionInfo
import org.termx.core.auth.SessionStore
import org.termx.terminology.fhir.codesystem.CodeSystemFhirMapper
import org.termx.terminology.terminology.codesystem.CodeSystemImportService
import org.termx.ts.codesystem.CodeSystemImportAction
import spock.lang.IgnoreIf

/**
 * Perf check for the churn fix: the second (re-)import computes a content signature for every concept
 * and holds the unchanged ones, so we want to confirm that work scales and does not regress a
 * large import. Gated behind the {@code PERF} env var so CI skips it; size via {@code PERF_N=<n>}.
 *
 *   ./gradlew :termx-integtest:test --tests '*CodeSystemImportPerfTest' PERF=true PERF_N=20000
 */
@MicronautTest(transactional = true)
@IgnoreIf({ env.PERF == null })
class CodeSystemImportPerfTest extends TermxIntegTest {
  @Inject CodeSystemImportService importService
  @Inject CodeSystemFhirMapper fhirMapper

  void setup() {
    def sessionInfo = new SessionInfo()
    sessionInfo.privileges = ['*.*.*']
    SessionStore.setLocal(sessionInfo)
  }

  void cleanup() {
    SessionStore.clearLocal()
  }

  def "first import vs unchanged re-import (signature + hold) of N concepts"() {
    given:
    int n = (System.getenv('PERF_N') ?: '20000') as int
    def json = buildJson(n)

    when: "first import — every concept is new"
    long t0 = System.currentTimeMillis()
    importService.importCodeSystem(domainCs(json), [], mergeAction())
    long firstMs = System.currentTimeMillis() - t0

    and: "identical re-import — every concept is compared and held"
    long t1 = System.currentTimeMillis()
    importService.importCodeSystem(domainCs(json), [], mergeAction())
    long reimportMs = System.currentTimeMillis() - t1

    then:
    println "PERF  n=${n}  firstImport=${firstMs}ms (${fmt(firstMs, n)})  reImportHold=${reimportMs}ms (${fmt(reimportMs, n)})"
    true
  }

  private static String fmt(long ms, int n) {
    return String.format("%.3f ms/concept", (ms as double) / n)
  }

  private static String buildJson(int n) {
    def concepts = new StringBuilder()
    for (int i = 1; i <= n; i++) {
      if (i > 1) concepts.append(",")
      concepts.append("""{"code":"c${i}","display":"Concept ${i}","designation":[{"language":"en","value":"Concept ${i} synonym"},{"language":"de","value":"Konzept ${i}"}],"property":[{"code":"p-decimal","valueDecimal":${i % 100}.5},{"code":"p-datetime","valueDateTime":"2025-06-01T12:30:45Z"}]}""")
    }
    return """{"resourceType":"CodeSystem","id":"perf-cs","url":"http://termx-test.local/CodeSystem/perf-cs","version":"1.0.0","name":"PerfCs","title":"Perf CS","status":"active","content":"complete","caseSensitive":true,"property":[{"code":"p-decimal","type":"decimal"},{"code":"p-datetime","type":"dateTime"}],"concept":[${concepts}]}"""
  }

  private org.termx.ts.codesystem.CodeSystem domainCs(String json) {
    def fhir = FhirMapper.fromJson(json, com.kodality.zmei.fhir.resource.terminology.CodeSystem)
    return fhirMapper.fromFhirCodeSystem(fhir)
  }

  private static CodeSystemImportAction mergeAction() {
    return new CodeSystemImportAction().setActivate(false).setCleanRun(false).setCleanConceptRun(false)
  }
}
