package org.termx.terminology.valueset

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

/**
 * Regression for PR #142 — {@code terminology.value_set_expand(bigint)} crashed with
 * Postgres "cannot cast jsonb object to type boolean" on
 * {@code POST /ts/code-systems/.../versions/1.0.9/activate} (the activate handler expands
 * referencing value sets).
 *
 * <p>The crash is purely in the SQL: the "match by properties" branch carried two unguarded
 * {@code (filter_ -> 'value')::boolean} casts for the {@code exists} operator. When a SIBLING
 * filter on the same rule holds a Coding-shaped object value
 * (e.g. {@code {"code":"CHROM","codeSystem":"…"}}), Postgres can evaluate the boolean cast on
 * that object even though it belongs to a different operator branch — OR/AND short-circuiting
 * through the CTE planner is not guaranteed — and the object→boolean cast throws.
 *
 * <p>This pins the function against that exact rule shape (an {@code exists} filter beside an
 * object-valued {@code =} filter). It is the guard the {@link
 * org.termx.terminology.fhir.valueset.operations.ValueSetExpandOperationTest} unit tests can't
 * give — they mock the repository, so only a real DB round-trip exercises the SQL cast. The fix
 * compares {@code ->> 'value'} (text form) to {@code 'true'}/{@code 'false'} instead of casting,
 * so an object value is a plain text inequality and never touches the type system.
 */
@MicronautTest(transactional = true)
class ValueSetExpandJsonbBooleanCastIT extends TermxIntegTest {
  @Inject CodeSystemImportService importService
  @Inject CodeSystemFhirMapper fhirMapper
  @Inject ValueSetService valueSetService
  @Inject ValueSetVersionService valueSetVersionService
  @Inject ValueSetVersionConceptRepository conceptRepository

  static final String CS_ID = "vsexpand-cs"
  static final String VS_ID = "vsexpand-vs"
  static final String VS_VERSION = "1.0.0"

  void setup() {
    def sessionInfo = new SessionInfo()
    sessionInfo.privileges = ['*.*.*']
    SessionStore.setLocal(sessionInfo)
  }

  void cleanup() {
    SessionStore.clearLocal()
  }

  def "expand of an 'exists' filter carrying an object value does not crash on the jsonb->boolean cast"() {
    given: "an active code system whose concept 'a' carries a 'method' property"
    def json = readFixtureString("fhir/vsexpand/cs.json")
    importService.importCodeSystem(domainCs(json), [], activatingImport())

    and: "a value set version whose rule carries an 'exists' filter with a Coding-shaped (object) value"
    // The field report's value is a Coding object {"code":"CHROM","codeSystem":"…"}. Carried on an
    // 'exists' filter it deterministically reaches the boolean cast: the old code evaluated
    // `(filter_ -> 'value')::boolean` whenever operator = 'exists', and casting a jsonb OBJECT to
    // boolean raises "cannot cast jsonb object to type boolean". A second, clean exists=true filter
    // gives a positive match so we can also assert the fixed function still expands normally.
    def cleanExistsFilter = new ValueSetRuleFilter()
        .setProperty(new PropertyReference().setName("method"))
        .setOperator("exists")
        .setValue(true)
    def objectExistsFilter = new ValueSetRuleFilter()
        .setProperty(new PropertyReference().setName("method"))
        .setOperator("exists")
        .setValue([code: "CHROM", codeSystem: CS_ID])

    def rule = new ValueSetVersionRule()
        .setType("include")
        .setCodeSystem(CS_ID)
        .setFilters([cleanExistsFilter, objectExistsFilter])

    def version = new ValueSetVersion()
        .setStatus(PublicationStatus.draft)
        .setRuleSet(new ValueSetVersionRuleSet().setRules([rule]))
    version.setValueSet(VS_ID)
    version.setVersion(VS_VERSION)

    def request = new ValueSetTransactionRequest()
    request.setValueSet(new ValueSet().setId(VS_ID).setUri("http://termx-test.local/ValueSet/" + VS_ID)
        .setTitle(new com.kodality.commons.model.LocalizedName().add("en", "VsExpand cast regression")))
    request.setVersion(version)
    valueSetService.save(request)

    when: "the stored version is expanded (the bigint variant the activate handler calls)"
    def versionId = valueSetVersionService.load(VS_ID, VS_VERSION).orElseThrow().getId()
    def expansion = conceptRepository.expand(versionId)

    then: "no DataIntegrityViolationException / cast error, and the 'exists'-matched concept is returned"
    noExceptionThrown()
    expansion.find { it.concept?.code == "a" } != null
  }

  private org.termx.ts.codesystem.CodeSystem domainCs(String json) {
    def fhir = FhirMapper.fromJson(json, com.kodality.zmei.fhir.resource.terminology.CodeSystem)
    return fhirMapper.fromFhirCodeSystem(fhir)
  }

  private static CodeSystemImportAction activatingImport() {
    return new CodeSystemImportAction().setActivate(true).setCleanRun(true).setCleanConceptRun(false)
  }

  private String readFixtureString(String relativePath) {
    def stream = getClass().getClassLoader().getResourceAsStream(relativePath)
    assert stream != null, "fixture not found: ${relativePath}"
    return new String(stream.readAllBytes(), "UTF-8")
  }
}
