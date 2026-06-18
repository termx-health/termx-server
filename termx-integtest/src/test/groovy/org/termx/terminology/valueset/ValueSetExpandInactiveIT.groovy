package org.termx.terminology.valueset

import com.kodality.commons.model.LocalizedName
import com.kodality.zmei.fhir.FhirMapper
import com.kodality.zmei.fhir.resource.other.Parameters
import com.kodality.zmei.fhir.resource.other.Parameters.ParametersParameter
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import org.termx.TermxIntegTest
import org.termx.core.auth.SessionInfo
import org.termx.core.auth.SessionStore
import org.termx.terminology.fhir.codesystem.CodeSystemFhirMapper
import org.termx.terminology.fhir.valueset.ValueSetFhirMapper
import org.termx.terminology.terminology.codesystem.CodeSystemImportService
import org.termx.terminology.terminology.valueset.ValueSetService
import org.termx.terminology.terminology.valueset.expansion.ValueSetVersionConceptService
import org.termx.terminology.terminology.valueset.version.ValueSetVersionService
import org.termx.ts.PublicationStatus
import org.termx.ts.codesystem.CodeSystemImportAction
import org.termx.ts.valueset.ValueSet
import org.termx.ts.valueset.ValueSetSnapshot
import org.termx.ts.valueset.ValueSetTransactionRequest
import org.termx.ts.valueset.ValueSetVersion
import org.termx.ts.valueset.ValueSetVersionRuleSet
import org.termx.ts.valueset.ValueSetVersionRuleSet.ValueSetVersionRule

/**
 * Pins the FHIR-conformant inactive-concept default: a value set expansion CONTAINS inactive concepts
 * (so {@code $expand} renders them with {@code inactive=true} and {@code $validate-code} can find them),
 * and they are dropped only at render time when {@code activeOnly=true}. Guards the flip in
 * {@link ValueSetVersionConceptService#expand} that stopped discarding inactive concepts from the
 * request-agnostic snapshot.
 *
 * <p>Fixture {@code fhir/inactive/codesystem-inactive.json}: codeActive (active), codeInactive
 * ({@code inactive=true}), codeRetired ({@code status=retired}).
 */
@MicronautTest(transactional = true)
class ValueSetExpandInactiveIT extends TermxIntegTest {
  @Inject CodeSystemImportService importService
  @Inject CodeSystemFhirMapper fhirMapper
  @Inject ValueSetService valueSetService
  @Inject ValueSetVersionService valueSetVersionService
  @Inject ValueSetVersionConceptService vsConceptService
  @Inject ValueSetFhirMapper valueSetFhirMapper

  static final String CS_ID = "inactive"
  static final String VS_ID = "vsexpand-inactive-vs"
  static final String VS_VERSION = "1.0.0"

  void setup() {
    def sessionInfo = new SessionInfo()
    sessionInfo.privileges = ['*.*.*']
    SessionStore.setLocal(sessionInfo)
    importInactiveCs()
    saveValueSet(VS_ID, null)
  }

  void cleanup() {
    SessionStore.clearLocal()
  }

  def "compose.inactive tri-state governs whether inactive concepts are in the request-agnostic expansion"() {
    given: "three value sets over the same code system differing only in compose.inactive"
    saveValueSet("vs-inactive-null", null)
    saveValueSet("vs-inactive-true", Boolean.TRUE)
    saveValueSet("vs-inactive-false", Boolean.FALSE)

    expect: "null (server default) and true include the retired concept; explicit false excludes it"
    codesOf("vs-inactive-null").contains("codeRetired")
    codesOf("vs-inactive-true").contains("codeRetired")
    !codesOf("vs-inactive-false").contains("codeRetired")

    and: "the active concept is present regardless"
    ["vs-inactive-null", "vs-inactive-true", "vs-inactive-false"].every { codesOf(it).contains("codeActive") }
  }

  def "the request-agnostic expansion contains the inactive concepts (no longer silently dropped)"() {
    when:
    def expansion = vsConceptService.expand(VS_ID, VS_VERSION)
    def codes = expansion.collect { it.concept?.code }.findAll { it != null } as Set

    then: "the active concept and both non-active concepts are present"
    codes == ["codeActive", "codeInactive", "codeRetired"] as Set

    and: "codeRetired (status=retired) is flagged inactive (active=false) on its expansion entry"
    !expansion.find { it.concept?.code == "codeRetired" }.active
  }

  def "default \$expand renders the inactive concept marked inactive=true; activeOnly=true drops it"() {
    given:
    def version = valueSetVersionService.load(VS_ID, VS_VERSION).orElseThrow()
    def valueSet = valueSetService.load(VS_ID)
    def concepts = vsConceptService.expand(VS_ID, VS_VERSION)
    def snapshot = new ValueSetSnapshot().setValueSet(VS_ID).setConceptsTotal(concepts.size()).setExpansion(concepts)

    when: "no activeOnly — codeRetired is included and flagged"
    def fhirDefault = valueSetFhirMapper.toFhir(valueSet, version, [], snapshot, new Parameters())
    def inactiveEntry = fhirDefault.expansion.contains.find { it.code == "codeRetired" }

    then:
    inactiveEntry != null
    inactiveEntry.inactive == true
    fhirDefault.expansion.contains*.code as Set == ["codeActive", "codeInactive", "codeRetired"] as Set

    when: "activeOnly=true — the inactive (retired) concept is excluded at render time"
    def activeOnly = new Parameters().setParameter([new ParametersParameter().setName("activeOnly").setValueBoolean(true)])
    def fhirActive = valueSetFhirMapper.toFhir(valueSet, version, [], snapshot, activeOnly)

    then: "codeRetired is dropped; codeActive (and any concept termx still treats as active) remain"
    !(fhirActive.expansion.contains*.code.contains("codeRetired"))
    fhirActive.expansion.contains*.code.contains("codeActive")
  }

  private void importInactiveCs() {
    def json = readFixtureString("fhir/inactive/codesystem-inactive.json")
    def fhir = FhirMapper.fromJson(json, com.kodality.zmei.fhir.resource.terminology.CodeSystem)
    def cs = fhirMapper.fromFhirCodeSystem(fhir)
    importService.importCodeSystem(cs, [], new CodeSystemImportAction().setActivate(true).setCleanRun(true).setCleanConceptRun(false))
  }

  private Set<String> codesOf(String vsId) {
    return vsConceptService.expand(vsId, VS_VERSION).collect { it.concept?.code }.findAll { it != null } as Set
  }

  private void saveValueSet(String vsId, Boolean inactive) {
    def rule = new ValueSetVersionRule().setType("include").setCodeSystem(CS_ID)
    def version = new ValueSetVersion()
        .setStatus(PublicationStatus.draft)
        .setRuleSet(new ValueSetVersionRuleSet().setInactive(inactive).setRules([rule]))
    version.setValueSet(vsId)
    version.setVersion(VS_VERSION)

    def request = new ValueSetTransactionRequest()
    request.setValueSet(new ValueSet().setId(vsId).setUri("http://termx-test.local/ValueSet/" + vsId)
        .setTitle(new LocalizedName().add("en", "Inactive expansion")))
    request.setVersion(version)
    valueSetService.save(request)
  }

  private String readFixtureString(String relativePath) {
    def stream = getClass().getClassLoader().getResourceAsStream(relativePath)
    assert stream != null, "fixture not found: ${relativePath}"
    return new String(stream.readAllBytes(), "UTF-8")
  }
}
