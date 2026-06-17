package org.termx.terminology.valueset

import com.kodality.commons.model.LocalizedName
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
 * Coverage for every FHIR ValueSet filter operator (https://build.fhir.org/valueset-filter-operator.html)
 * against the STORED-version expansion path — {@code terminology.value_set_expand(bigint)} in
 * {@code 01-value_set_expand.sql}, reached via {@link ValueSetVersionConceptRepository#expand(Long)}.
 *
 * <p>Issue #197 asks termx to support the full filter-operator set. This IT pins the expected
 * member set for each operator against a known hierarchy so any regression (or future SQL rewrite)
 * is caught. The sibling {@link ValueSetExpandInlineFilterOperatorIT} pins the same operators on
 * the inline {@code value_set_expand(text)} path, which today implements only a subset.
 *
 * <p>Fixture hierarchy ({@code cs-hierarchy.json}, hierarchyMeaning = is-a):
 * <pre>
 *   A                      (property method = CHROM)
 *   ├── B                  (property method = CHROM)
 *   │   └── D
 *   └── C
 *   X                      (separate root, no parent)
 * </pre>
 * Associations (is-a): B→A, C→A, D→B. {@code method} is set on A and B only.
 */
@MicronautTest(transactional = true)
class ValueSetExpandFilterOperatorIT extends TermxIntegTest {
  @Inject CodeSystemImportService importService
  @Inject CodeSystemFhirMapper fhirMapper
  @Inject ValueSetService valueSetService
  @Inject ValueSetVersionService valueSetVersionService
  @Inject ValueSetVersionConceptRepository conceptRepository

  static final String CS_ID = "vsexpand-filter-cs"
  static final String VS_ID = "vsexpand-filter-vs"
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

  def "is-a includes the concept itself plus all transitive descendants"() {
    expect:
    expand(filter("concept", "is-a", "A")) == ["A", "B", "C", "D"] as Set
  }

  def "descendent-of includes all transitive descendants but excludes the concept itself"() {
    expect:
    expand(filter("concept", "descendent-of", "A")) == ["B", "C", "D"] as Set
  }

  def "child-of includes only direct children, excluding the concept itself and deeper descendants"() {
    expect:
    expand(filter("concept", "child-of", "A")) == ["B", "C"] as Set
  }

  def "descendent-leaf includes only descendants that are themselves leaves"() {
    expect: "B has a child (D) so it is excluded; C and D are leaves"
    expand(filter("concept", "descendent-leaf", "A")) == ["C", "D"] as Set
  }

  def "is-not-a includes every concept that is NOT in the is-a set of the value"() {
    expect: "only X lives outside the A subtree"
    expand(filter("concept", "is-not-a", "A")) == ["X"] as Set
  }

  def "generalizes includes the concept itself plus all transitive ancestors"() {
    expect:
    expand(filter("concept", "generalizes", "D")) == ["A", "B", "D"] as Set
  }

  def "'=' on code matches the single concept with that code"() {
    expect:
    expand(filter("code", "=", "C")) == ["C"] as Set
  }

  def "'in' on code matches every concept whose code is in the comma-separated set"() {
    expect:
    expand(filter("code", "in", "B,C")) == ["B", "C"] as Set
  }

  def "'not-in' on code matches every concept whose code is NOT in the comma-separated set"() {
    expect:
    expand(filter("code", "not-in", "B,C")) == ["A", "D", "X"] as Set
  }

  def "regex on code matches every concept whose code matches the pattern"() {
    expect:
    expand(filter("code", "regex", "[BD]")) == ["B", "D"] as Set
  }

  def "exists matches every concept that has at least one value of the property"() {
    expect: "method is populated on A and B only"
    expand(filter("method", "exists", true)) == ["A", "B"] as Set
  }

  def "exists=false matches every concept that has NO value of the property"() {
    expect: "the complement of the exists=true set"
    expand(filter("method", "exists", false)) == ["C", "D", "X"] as Set
  }

  def "'=' on a custom property matches concepts whose property equals the value"() {
    expect: "method = CHROM on A and B"
    expand(filter("method", "=", "CHROM")) == ["A", "B"] as Set
  }

  def "'in' on a string custom property matches concepts whose property is in the set"() {
    expect:
    expand(filter("method", "in", "CHROM,OTHER")) == ["A", "B"] as Set
  }

  def "'=' on a Coding custom property matches by code"() {
    expect: "coded.code = cd-a on A"
    expand(filter("coded", "=", "cd-a")) == ["A"] as Set
  }

  def "'in' on a Coding custom property matches by code"() {
    expect:
    expand(filter("coded", "in", "cd-a,cd-b")) == ["A", "B"] as Set
  }

  // --- helpers ---------------------------------------------------------------

  private Set<String> expand(ValueSetRuleFilter... filters) {
    def rule = new ValueSetVersionRule()
        .setType("include")
        .setCodeSystem(CS_ID)
        .setFilters(filters.toList())

    def version = new ValueSetVersion()
        .setStatus(PublicationStatus.draft)
        .setRuleSet(new ValueSetVersionRuleSet().setRules([rule]))
    version.setValueSet(VS_ID)
    version.setVersion(VS_VERSION)

    def request = new ValueSetTransactionRequest()
    request.setValueSet(new ValueSet().setId(VS_ID).setUri("http://termx-test.local/ValueSet/" + VS_ID)
        .setTitle(new LocalizedName().add("en", "Filter operator expansion")))
    request.setVersion(version)
    valueSetService.save(request)

    def versionId = valueSetVersionService.load(VS_ID, VS_VERSION).orElseThrow().getId()
    return conceptRepository.expand(versionId).collect { it.concept?.code }.findAll { it != null } as Set
  }

  private static ValueSetRuleFilter filter(String property, String operator, Object value) {
    return new ValueSetRuleFilter()
        .setProperty(new PropertyReference().setName(property))
        .setOperator(operator)
        .setValue(value)
  }

  private void importHierarchyCs() {
    def json = readFixtureString("fhir/vsexpand/cs-hierarchy.json")
    def fhir = FhirMapper.fromJson(json, com.kodality.zmei.fhir.resource.terminology.CodeSystem)
    def cs = fhirMapper.fromFhirCodeSystem(fhir)
    importService.importCodeSystem(cs, [], new CodeSystemImportAction().setActivate(true).setCleanRun(true).setCleanConceptRun(false))
  }

  private String readFixtureString(String relativePath) {
    def stream = getClass().getClassLoader().getResourceAsStream(relativePath)
    assert stream != null, "fixture not found: ${relativePath}"
    return new String(stream.readAllBytes(), "UTF-8")
  }
}
