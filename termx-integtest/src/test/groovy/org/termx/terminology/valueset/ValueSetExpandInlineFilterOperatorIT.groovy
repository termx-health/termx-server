package org.termx.terminology.valueset

import com.kodality.commons.util.JsonUtil
import com.kodality.zmei.fhir.FhirMapper
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import org.termx.TermxIntegTest
import org.termx.core.auth.SessionInfo
import org.termx.core.auth.SessionStore
import org.termx.terminology.fhir.codesystem.CodeSystemFhirMapper
import org.termx.terminology.terminology.codesystem.CodeSystemImportService
import org.termx.terminology.terminology.codesystem.version.CodeSystemVersionRepository
import org.termx.terminology.terminology.codesystem.version.CodeSystemVersionService
import org.termx.terminology.terminology.valueset.expansion.ValueSetVersionConceptRepository
import org.termx.ts.codesystem.CodeSystemImportAction

/**
 * Coverage for every FHIR ValueSet filter operator against the INLINE expansion path —
 * {@code terminology.value_set_expand(text)} in {@code 37-value_set_expand_jsonb.sql}, reached via
 * {@link ValueSetVersionConceptRepository#expandFromJson(String)}. This is the path used by FHIR
 * {@code $expand} of a posted/inline ValueSet and by the rule-preview service.
 *
 * <p>Mirrors {@link ValueSetExpandFilterOperatorIT} (stored-version path) so the two paths are held
 * to the same FHIR semantics — see https://build.fhir.org/valueset-filter-operator.html. The inline
 * SQL function was brought to operator parity for issue #197.
 *
 * <p>Fixture hierarchy ({@code cs-hierarchy.json}, hierarchyMeaning = is-a):
 * <pre>
 *   A (method=CHROM) ├── B (method=CHROM) └── D
 *                    └── C
 *   X (separate root)
 * </pre>
 */
@MicronautTest(transactional = true)
class ValueSetExpandInlineFilterOperatorIT extends TermxIntegTest {
  @Inject CodeSystemImportService importService
  @Inject CodeSystemFhirMapper fhirMapper
  @Inject ValueSetVersionConceptRepository conceptRepository
  @Inject CodeSystemVersionService codeSystemVersionService
  @Inject CodeSystemVersionRepository codeSystemVersionRepository

  static final String CS_ID = "vsexpand-filter-cs"
  static final String CS_URI = "http://termx-test.local/CodeSystem/vsexpand-filter-cs"
  static final String CS_VERSION = "1.0.0"

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
    expandInline("concept", "is-a", "A") == ["A", "B", "C", "D"] as Set
  }

  def "descendent-of includes all transitive descendants but excludes the concept itself"() {
    expect:
    expandInline("concept", "descendent-of", "A") == ["B", "C", "D"] as Set
  }

  def "child-of includes only direct children"() {
    expect:
    expandInline("concept", "child-of", "A") == ["B", "C"] as Set
  }

  def "descendent-leaf includes only descendants that are themselves leaves"() {
    expect: "B has a child (D) so it is excluded; C and D are leaves"
    expandInline("concept", "descendent-leaf", "A") == ["C", "D"] as Set
  }

  def "is-not-a includes every concept that is NOT in the is-a set of the value"() {
    expect:
    expandInline("concept", "is-not-a", "A") == ["X"] as Set
  }

  def "generalizes includes the concept itself plus all transitive ancestors"() {
    expect:
    expandInline("concept", "generalizes", "D") == ["A", "B", "D"] as Set
  }

  def "'=' on code matches the single concept with that code"() {
    expect:
    expandInline("code", "=", "C") == ["C"] as Set
  }

  def "'in' on code matches every concept whose code is in the comma-separated set"() {
    expect:
    expandInline("code", "in", "B,C") == ["B", "C"] as Set
  }

  def "'not-in' on code matches every concept whose code is NOT in the comma-separated set"() {
    expect:
    expandInline("code", "not-in", "B,C") == ["A", "D", "X"] as Set
  }

  def "regex on code matches every concept whose code matches the pattern"() {
    expect:
    expandInline("code", "regex", "[BD]") == ["B", "D"] as Set
  }

  def "exists matches every concept that has at least one value of the property"() {
    expect: "method is populated on A and B only"
    expandInline("method", "exists", true) == ["A", "B"] as Set
  }

  def "exists=false matches every concept that has NO value of the property"() {
    expect:
    expandInline("method", "exists", false) == ["C", "D", "X"] as Set
  }

  def "'=' on a string custom property matches concepts whose property equals the value"() {
    expect: "method = CHROM on A and B"
    expandInline("method", "=", "CHROM") == ["A", "B"] as Set
  }

  def "'in' on a string custom property matches concepts whose property is in the set"() {
    expect:
    expandInline("method", "in", "CHROM,OTHER") == ["A", "B"] as Set
  }

  def "'=' on a Coding custom property matches by code (scalar value)"() {
    expect: "coded.code = cd-a on A"
    expandInline("coded", "=", "cd-a") == ["A"] as Set
  }

  def "'=' on a Coding custom property matches by code (Coding-object value)"() {
    expect:
    expandInline("coded", "=", [code: "cd-a"]) == ["A"] as Set
  }

  def "'in' on a Coding custom property matches by code"() {
    expect:
    expandInline("coded", "in", "cd-a,cd-b") == ["A", "B"] as Set
  }

  def "legacy 'parent =' shorthand returns the direct children of the value (child-of semantics)"() {
    expect: "parent = A => direct children of A only (B, C), not deeper descendants"
    expandInline("parent", "=", "A") == ["B", "C"] as Set
  }

  def "include.version is resolved by the version's canonical URI, not only the business version"() {
    given: "the code system version carries a canonical URI, as FHIR-defined nomenclatures do"
    def versionUri = CS_URI + "|" + CS_VERSION
    def version = codeSystemVersionService.load(CS_ID, CS_VERSION).orElseThrow()
    version.setUri(versionUri)
    codeSystemVersionRepository.save(version)

    expect: "expanding with include.version = the canonical URI resolves the same concepts as the business version"
    // ValueSetFhirMapper emits the version URI (not '1.0.0') in compose.include.version when the
    // version has a uri; the rule-preview/$expand path must still resolve it. Before this fix, it
    // returned an empty set because value_set_expand(text) only matched csv.version.
    expandInline("method", "=", "CHROM", versionUri) == ["A", "B"] as Set
  }

  // --- helpers ---------------------------------------------------------------

  private Set<String> expandInline(String property, String op, Object value, String version = CS_VERSION) {
    def valueSet = [
        resourceType: "ValueSet",
        compose      : [
            inactive: false,
            include : [[
                           system : CS_URI,
                           version: version,
                           filter : [[property: property, op: op, value: value]]
                       ]]
        ]
    ]
    def json = JsonUtil.toJson(valueSet)
    return conceptRepository.expandFromJson(json).collect { it.concept?.code }.findAll { it != null } as Set
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
