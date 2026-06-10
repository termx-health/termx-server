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

  def "CLEAN-VERSION: re-importing identical content holds every concept version (no churn)"() {
    given:
    def cs = "churn-clean-identical"
    def json = fixtureFor(cs)

    when: "imported clean-version, then re-imported with identical content"
    importService.importCodeSystem(domainCs(json), [], cleanAction())
    def a0 = versionIds(cs, "a")
    def b0 = versionIds(cs, "b")
    importService.importCodeSystem(domainCs(json), [], cleanAction())

    then: "the version is reconciled in place — same concept versions kept, nothing churns"
    a0.size() == 1
    b0.size() == 1
    versionIds(cs, "a") == a0
    versionIds(cs, "b") == b0
  }

  def "CLEAN-VERSION: a changed concept gets exactly one new version"() {
    given:
    def cs = "churn-clean-changed"
    def json = fixtureFor(cs)

    when: "imported clean-version, then re-imported with a changed decimal on 'a'"
    importService.importCodeSystem(domainCs(json), [], cleanAction())
    def a0 = versionIds(cs, "a")
    def b0 = versionIds(cs, "b")
    importService.importCodeSystem(domainCs(json.replace('"valueDecimal": 3.14', '"valueDecimal": 9.99')), [], cleanAction())

    then: "'a' gets a new version; unchanged 'b' is held"
    a0.size() == 1
    versionIds(cs, "a") != a0
    versionIds(cs, "a").size() == 2
    versionIds(cs, "b") == b0
  }

  def "CLEAN-VERSION: a concept removed from the file is retired, the rest held"() {
    given:
    def cs = "churn-clean-removed"
    def json = fixtureFor(cs)

    when: "imported clean-version, then re-imported with concept 'b' removed"
    importService.importCodeSystem(domainCs(json), [], cleanAction())
    def a0 = versionIds(cs, "a")
    def withoutB = domainCs(json)
    withoutB.setConcepts(withoutB.getConcepts().findAll { it.code != "b" })
    importService.importCodeSystem(withoutB, [], cleanAction())

    then: "'a' is held; 'b' is retired (no active version remains)"
    versionIds(cs, "a") == a0
    activeVersionIds(cs, "b").isEmpty()
  }

  private org.termx.ts.codesystem.CodeSystem domainCs(String json) {
    def fhir = FhirMapper.fromJson(json, com.kodality.zmei.fhir.resource.terminology.CodeSystem)
    return fhirMapper.fromFhirCodeSystem(fhir)
  }

  /** The shared fixture, retargeted to a unique code-system id so each test is independent. */
  private String fixtureFor(String csId) {
    def json = readFixtureString("fhir/churn/cs.json")
    return json.replace('"id": "churn-cs"', '"id": "' + csId + '"').replace('/churn-cs"', '/' + csId + '"')
  }

  private static CodeSystemImportAction mergeAction() {
    return new CodeSystemImportAction().setActivate(false).setCleanRun(false).setCleanConceptRun(false)
  }

  private static CodeSystemImportAction cleanAction() {
    return new CodeSystemImportAction().setActivate(true).setCleanRun(true).setCleanConceptRun(false)
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
