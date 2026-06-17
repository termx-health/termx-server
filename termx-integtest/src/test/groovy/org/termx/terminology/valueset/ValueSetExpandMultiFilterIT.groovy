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
 * Issue #196 — multiple filters within a single value set rule must combine with logical AND
 * (FHIR {@code compose.include[].filter[]} semantics: a concept must satisfy EVERY filter).
 * Pinned against both expansion paths: stored {@code value_set_expand(bigint)} and inline
 * {@code value_set_expand(text)}.
 *
 * <p>Fixture {@code cs-hierarchy.json}: A → {B → D, C}, plus root X; {@code method} on A and B.
 * So {@code is-a A} = {A,B,C,D}, {@code exists method} = {A,B}; their AND = {A,B}.
 * And {@code descendent-of A} = {B,C,D} AND {@code exists method} = {A,B} → {B}.
 */
@MicronautTest(transactional = true)
class ValueSetExpandMultiFilterIT extends TermxIntegTest {
  @Inject CodeSystemImportService importService
  @Inject CodeSystemFhirMapper fhirMapper
  @Inject ValueSetService valueSetService
  @Inject ValueSetVersionService valueSetVersionService
  @Inject ValueSetVersionConceptRepository conceptRepository

  static final String CS_ID = "vsexpand-filter-cs"
  static final String CS_URI = "http://termx-test.local/CodeSystem/vsexpand-filter-cs"
  static final String VS_ID = "vsexpand-multi-vs"
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

  def "stored path ANDs two filters in one rule (is-a A AND exists method = A,B)"() {
    expect:
    expandStored([f("concept", "is-a", "A"), f("method", "exists", true)]) == ["A", "B"] as Set
  }

  def "stored path ANDs descendent-of A AND exists method = B"() {
    expect:
    expandStored([f("concept", "descendent-of", "A"), f("method", "exists", true)]) == ["B"] as Set
  }

  def "inline path ANDs two filters in one rule (is-a A AND exists method = A,B)"() {
    expect:
    expandInline([[property: "concept", op: "is-a", value: "A"], [property: "method", op: "exists", value: true]]) == ["A", "B"] as Set
  }

  def "inline path ANDs descendent-of A AND exists method = B"() {
    expect:
    expandInline([[property: "concept", op: "descendent-of", value: "A"], [property: "method", op: "exists", value: true]]) == ["B"] as Set
  }

  def "stored path ORs across multiple include rules (filters AND within a rule, rules OR)"() {
    given: "rule 1 = (child-of A AND exists method) = {B}; rule 2 = code in X,C = {C,X}"
    def rule1 = new ValueSetVersionRule().setType("include").setCodeSystem(CS_ID)
        .setFilters([f("concept", "child-of", "A"), f("method", "exists", true)])
    def rule2 = new ValueSetVersionRule().setType("include").setCodeSystem(CS_ID)
        .setFilters([f("code", "in", "X,C")])

    expect: "the value set is the union of the two rules"
    expandStoredRules([rule1, rule2]) == ["B", "C", "X"] as Set
  }

  def "inline path ORs across multiple include blocks"() {
    given: "include 1 = (child-of A AND exists method) = {B}; include 2 = code in X,C = {C,X}"
    def include1 = [[property: "concept", op: "child-of", value: "A"], [property: "method", op: "exists", value: true]]
    def include2 = [[property: "code", op: "in", value: "X,C"]]

    expect:
    expandInlineIncludes([include1, include2]) == ["B", "C", "X"] as Set
  }

  // --- helpers ---------------------------------------------------------------

  private Set<String> expandStoredRules(List<ValueSetVersionRule> rules) {
    def version = new ValueSetVersion().setStatus(PublicationStatus.draft)
        .setRuleSet(new ValueSetVersionRuleSet().setRules(rules))
    version.setValueSet(VS_ID)
    version.setVersion(VS_VERSION)
    def request = new ValueSetTransactionRequest()
    request.setValueSet(new ValueSet().setId(VS_ID).setUri("http://termx-test.local/ValueSet/" + VS_ID)
        .setTitle(new LocalizedName().add("en", "Multi filter")))
    request.setVersion(version)
    valueSetService.save(request)
    def versionId = valueSetVersionService.load(VS_ID, VS_VERSION).orElseThrow().getId()
    return conceptRepository.expand(versionId).collect { it.concept?.code }.findAll { it != null } as Set
  }

  private Set<String> expandInlineIncludes(List includes) {
    def valueSet = [resourceType: "ValueSet", compose: [inactive: false,
        include: includes.collect { [system: CS_URI, version: VS_VERSION, filter: it] }]]
    return conceptRepository.expandFromJson(JsonUtil.toJson(valueSet)).collect { it.concept?.code }.findAll { it != null } as Set
  }

  private Set<String> expandStored(List<ValueSetRuleFilter> filters) {
    def rule = new ValueSetVersionRule().setType("include").setCodeSystem(CS_ID).setFilters(filters)
    def version = new ValueSetVersion().setStatus(PublicationStatus.draft)
        .setRuleSet(new ValueSetVersionRuleSet().setRules([rule]))
    version.setValueSet(VS_ID)
    version.setVersion(VS_VERSION)
    def request = new ValueSetTransactionRequest()
    request.setValueSet(new ValueSet().setId(VS_ID).setUri("http://termx-test.local/ValueSet/" + VS_ID)
        .setTitle(new LocalizedName().add("en", "Multi filter")))
    request.setVersion(version)
    valueSetService.save(request)
    def versionId = valueSetVersionService.load(VS_ID, VS_VERSION).orElseThrow().getId()
    return conceptRepository.expand(versionId).collect { it.concept?.code }.findAll { it != null } as Set
  }

  private Set<String> expandInline(List filters) {
    def valueSet = [resourceType: "ValueSet", compose: [inactive: false,
        include: [[system: CS_URI, version: VS_VERSION, filter: filters]]]]
    return conceptRepository.expandFromJson(JsonUtil.toJson(valueSet)).collect { it.concept?.code }.findAll { it != null } as Set
  }

  private static ValueSetRuleFilter f(String property, String operator, Object value) {
    return new ValueSetRuleFilter().setProperty(new PropertyReference().setName(property)).setOperator(operator).setValue(value)
  }

  private void importHierarchyCs() {
    def stream = getClass().getClassLoader().getResourceAsStream("fhir/vsexpand/cs-hierarchy.json")
    assert stream != null
    def json = new String(stream.readAllBytes(), "UTF-8")
    def fhir = FhirMapper.fromJson(json, com.kodality.zmei.fhir.resource.terminology.CodeSystem)
    importService.importCodeSystem(fhirMapper.fromFhirCodeSystem(fhir), [],
        new CodeSystemImportAction().setActivate(true).setCleanRun(true).setCleanConceptRun(false))
  }
}
