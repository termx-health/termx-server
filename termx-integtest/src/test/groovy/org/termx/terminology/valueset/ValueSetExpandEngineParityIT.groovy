package org.termx.terminology.valueset

import com.kodality.commons.model.LocalizedName
import com.kodality.zmei.fhir.FhirMapper
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import org.termx.TermxIntegTest
import org.termx.core.auth.SessionInfo
import org.termx.core.auth.SessionStore
import org.termx.terminology.fhir.codesystem.CodeSystemFhirMapper
import org.termx.terminology.fhir.valueset.ValueSetFhirMapper
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
import org.termx.ts.valueset.ValueSetVersionConcept
import org.termx.ts.valueset.ValueSetVersionRuleSet
import org.termx.ts.valueset.ValueSetVersionRuleSet.ValueSetVersionRule
import org.termx.ts.valueset.ValueSetVersionRuleSet.ValueSetVersionRule.ValueSetRuleFilter
import spock.lang.Unroll

/**
 * Engine-parity gate for the planned 01/37 expansion unification. The two SQL engines —
 * {@code value_set_expand(bigint)} (stored, fn01, via {@link ValueSetVersionConceptRepository#expand})
 * and {@code value_set_expand(text)} (inline, fn37, via {@link ValueSetVersionConceptRepository#expandFromJson})
 * — must return the SAME concept set for the same value-set definition. This expands one stored version
 * through BOTH paths (fn37 is fed the FHIR JSON produced by {@link ValueSetFhirMapper#toFhir}) and asserts
 * equality, so any future divergence (or the eventual single-engine swap) is caught here.
 *
 * <p>Fixture {@code cs-hierarchy.json}: A(method=CHROM), B(method=CHROM)→A, C→A, D→B, X (separate root).
 */
@MicronautTest(transactional = true)
class ValueSetExpandEngineParityIT extends TermxIntegTest {
  @Inject CodeSystemImportService importService
  @Inject CodeSystemFhirMapper csFhirMapper
  @Inject ValueSetFhirMapper vsFhirMapper
  @Inject ValueSetService valueSetService
  @Inject ValueSetVersionService valueSetVersionService
  @Inject ValueSetVersionConceptRepository conceptRepository

  static final String CS_ID = "vsexpand-filter-cs"
  static final String CS_URI = "http://termx-test.local/CodeSystem/vsexpand-filter-cs"
  static final String VS_ID = "vsexpand-parity-vs"
  static final String VS_VERSION = "1.0.0"

  void setup() {
    def sessionInfo = new SessionInfo()
    sessionInfo.privileges = ['*.*.*']
    SessionStore.setLocal(sessionInfo)
    importHierarchyCs()
  }

  void cleanup() {
    SessionStore.clearLocal()
  }

  @Unroll
  def "fn01 (stored) and fn37 (inline) agree for #name"() {
    when:
    def both = expandBothWays(rule)

    then: "the two engines return the same concept set, and it matches the expected set"
    both.stored == expected as Set
    both.inline == both.stored

    where:
    name                    | rule                                                              || expected
    "all concepts"          | allConcepts()                                                     || ["A", "B", "C", "D", "X"]
    "exact concepts"        | exactConcepts("A", "C")                                           || ["A", "C"]
    "property = (string)"   | filtered(filter("method", "=", "CHROM"))                          || ["A", "B"]
    "code in"               | filtered(filter("code", "in", "B,C"))                             || ["B", "C"]
    "concept is-a"          | filtered(filter("concept", "is-a", "A"))                          || ["A", "B", "C", "D"]
    "concept descendent-of" | filtered(filter("concept", "descendent-of", "A"))                 || ["B", "C", "D"]
  }

  // --- helpers ---------------------------------------------------------------

  private Map expandBothWays(ValueSetVersionRule rule) {
    def version = new ValueSetVersion()
        .setStatus(PublicationStatus.draft)
        .setPreferredLanguage("en")
        .setRuleSet(new ValueSetVersionRuleSet().setRules([rule]))
    version.setValueSet(VS_ID)
    version.setVersion(VS_VERSION)

    def vs = new ValueSet().setId(VS_ID).setUri("http://termx-test.local/ValueSet/" + VS_ID)
        .setTitle(new LocalizedName().add("en", "Engine parity"))
    def request = new ValueSetTransactionRequest()
    request.setValueSet(vs)
    request.setVersion(version)
    valueSetService.save(request)
    def versionId = valueSetVersionService.load(VS_ID, VS_VERSION).orElseThrow().getId()

    // fn01 — stored path, by version id
    def stored = conceptRepository.expand(versionId).collect { it.concept?.code }.findAll { it != null } as Set

    // fn37 — inline path, fed the FHIR JSON of the same version
    def fhirJson = FhirMapper.toJson(vsFhirMapper.toFhir(vs, version, []))
    def inline = conceptRepository.expandFromJson(fhirJson).collect { it.concept?.code }.findAll { it != null } as Set

    return [stored: stored, inline: inline]
  }

  private ValueSetVersionRule allConcepts() {
    return new ValueSetVersionRule().setType("include").setCodeSystem(CS_ID).setCodeSystemUri(CS_URI)
  }

  private ValueSetVersionRule exactConcepts(String... codes) {
    def concepts = codes.collect { c ->
      new ValueSetVersionConcept().setConcept(new ValueSetVersionConcept.ValueSetVersionConceptValue().setCode(c))
    }
    return new ValueSetVersionRule().setType("include").setCodeSystem(CS_ID).setCodeSystemUri(CS_URI).setConcepts(concepts)
  }

  private ValueSetVersionRule filtered(ValueSetRuleFilter... filters) {
    return new ValueSetVersionRule().setType("include").setCodeSystem(CS_ID).setCodeSystemUri(CS_URI).setFilters(filters.toList())
  }

  private static ValueSetRuleFilter filter(String property, String operator, Object value) {
    return new ValueSetRuleFilter().setProperty(new PropertyReference().setName(property)).setOperator(operator).setValue(value)
  }

  private void importHierarchyCs() {
    def json = readFixtureString("fhir/vsexpand/cs-hierarchy.json")
    def fhir = FhirMapper.fromJson(json, com.kodality.zmei.fhir.resource.terminology.CodeSystem)
    def cs = csFhirMapper.fromFhirCodeSystem(fhir)
    importService.importCodeSystem(cs, [], new CodeSystemImportAction().setActivate(true).setCleanRun(true).setCleanConceptRun(false))
  }

  private String readFixtureString(String relativePath) {
    def stream = getClass().getClassLoader().getResourceAsStream(relativePath)
    assert stream != null, "fixture not found: ${relativePath}"
    return new String(stream.readAllBytes(), "UTF-8")
  }
}
