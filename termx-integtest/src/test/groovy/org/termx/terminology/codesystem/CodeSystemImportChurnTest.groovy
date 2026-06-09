package org.termx.terminology.codesystem

import com.kodality.zmei.fhir.FhirMapper
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import org.termx.TermxIntegTest
import org.termx.core.auth.SessionInfo
import org.termx.core.auth.SessionStore
import org.termx.terminology.fhir.codesystem.CodeSystemFhirMapper
import org.termx.terminology.terminology.codesystem.CodeSystemImportService
import org.termx.terminology.terminology.codesystem.entity.CodeSystemEntityVersionService
import org.termx.ts.codesystem.CodeSystemEntityVersionQueryParams
import org.termx.ts.codesystem.CodeSystemImportAction

/**
 * DB round-trip for the concept-version churn fix (#176/#178), exercising the MERGE path the fix
 * targets ({@code cleanRun=false} — no clean-version rebuild): re-importing an unchanged concept must
 * reuse its existing entity version, and a real change must produce a new one.
 *
 * This is the end-to-end guard the unit tests can't give — they mock the existing-version lookup, so
 * only a real DB round-trip proves the content comparison (incl. canonical designation order) holds
 * unchanged concepts. The fixture concepts carry multiple designations and typed (decimal/dateTime)
 * properties — the shapes whose load order / formatting used to defeat the comparison.
 *
 * NB: this drives {@link CodeSystemImportService} directly with {@code cleanRun=false}. The FHIR/clean
 * import path ({@code cleanRun=true}) cancels and recreates the whole CS version by design, which is a
 * separate (version-rebuild) behavior, not the per-concept merge this fix addresses.
 */
@MicronautTest(transactional = true)
class CodeSystemImportChurnTest extends TermxIntegTest {
  @Inject CodeSystemImportService importService
  @Inject CodeSystemFhirMapper fhirMapper
  @Inject CodeSystemEntityVersionService entityVersionService

  void setup() {
    def sessionInfo = new SessionInfo()
    sessionInfo.privileges = ['*.*.*']
    SessionStore.setLocal(sessionInfo)
  }

  void cleanup() {
    SessionStore.clearLocal()
  }

  def "merge re-import of an unchanged (active) concept reuses its version; a change makes a new one"() {
    given:
    def json = readFixtureString("fhir/churn/cs.json")
    def csId = "churn-cs"

    when: "first import (draft), then concept 'a' version is activated"
    importService.importCodeSystem(domainCs(json), [], mergeAction())
    def a0 = versionIds(csId, "a")
    entityVersionService.activate(csId, new ArrayList<>(a0))

    then: "one version, now active"
    a0.size() == 1
    activeVersionIds(csId, "a") == a0

    when: "the identical content is re-imported (merge)"
    importService.importCodeSystem(domainCs(json), [], mergeAction())

    then: "the SAME version is kept — no churn (the customer fix)"
    versionIds(csId, "a") == a0
    activeVersionIds(csId, "a") == a0

    when: "concept 'a' changes (a decimal property value) and is imported again"
    importService.importCodeSystem(domainCs(json.replace('"valueDecimal": 3.14', '"valueDecimal": 9.99')), [], mergeAction())

    then: "'a' now has an additional version"
    versionIds(csId, "a") != a0
    versionIds(csId, "a").size() == 2
  }

  private org.termx.ts.codesystem.CodeSystem domainCs(String json) {
    def fhir = FhirMapper.fromJson(json, com.kodality.zmei.fhir.resource.terminology.CodeSystem)
    return fhirMapper.fromFhirCodeSystem(fhir)
  }

  private static CodeSystemImportAction mergeAction() {
    return new CodeSystemImportAction().setActivate(false).setCleanRun(false).setCleanConceptRun(false)
  }

  private Set<Long> versionIds(String csId, String code) {
    return ids(csId, code, "active,draft,retired")
  }

  private Set<Long> activeVersionIds(String csId, String code) {
    return ids(csId, code, "active")
  }

  private Set<Long> ids(String csId, String code, String status) {
    def params = new CodeSystemEntityVersionQueryParams()
    params.setCodeSystem(csId)
    params.setCode(code)
    params.setStatus(status)
    params.all()
    return entityVersionService.query(params).getData().collect { it.id } as Set
  }

  private String readFixtureString(String relativePath) {
    def stream = getClass().getClassLoader().getResourceAsStream(relativePath)
    assert stream != null, "fixture not found: ${relativePath}"
    return new String(stream.readAllBytes(), "UTF-8")
  }
}
