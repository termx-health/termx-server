package org.termx.terminology.valueset

import com.kodality.zmei.fhir.FhirMapper
import com.kodality.zmei.fhir.resource.other.Parameters
import com.kodality.zmei.fhir.resource.other.Parameters.ParametersParameter
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import org.termx.TermxIntegTest
import org.termx.core.auth.SessionInfo
import org.termx.core.auth.SessionStore
import org.termx.terminology.fhir.codesystem.CodeSystemFhirImportService
import org.termx.terminology.fhir.valueset.ValueSetFhirImportService
import org.termx.terminology.fhir.valueset.operations.ValueSetExpandOperation

/**
 * RED tests for issue #228 — the FHIR tx-ecosystem ships multiple versions of one canonical resource as
 * SEPARATE resources that share a {@code url} but have distinct ids. TermX has a unique index on
 * {@code value_set.uri} ({@code value_set_ukey}), so the second such ValueSet fails to import (500), and the
 * versioned {@code $expand} then can't find it.
 *
 * <p>Expected behavior (to be implemented): two ValueSets with one canonical url but distinct ids should be
 * folded into a single canonical resource carrying both versions, and {@code $expand} by {@code valueSetVersion}
 * should resolve each version's own definition. (Same-id multi-version already works — this is the distinct-id
 * case the suite exercises.)
 */
@MicronautTest(transactional = true)
class ValueSetVersionResolutionIT extends TermxIntegTest {
  @Inject CodeSystemFhirImportService csImport
  @Inject ValueSetFhirImportService vsImport
  @Inject ValueSetExpandOperation vsExpand

  static final String CS_URL = "http://termx-test.local/CodeSystem/vsvr-cs"
  static final String VS_URL = "http://termx-test.local/ValueSet/vsvr"

  void setup() {
    SessionStore.setLocal(new SessionInfo().setPrivileges(["*.*.*"] as Set))
    csImport.importCodeSystem(baseCs(), "vsvr-cs")
  }

  void cleanup() {
    SessionStore.clearLocal()
  }

  def "two value sets with one canonical url but distinct ids both load and \$expand resolves by version"() {
    given: "v1 (id vsvr-1) includes c1; v2 (id vsvr-2, SAME url) includes c1+c2"
    vsImport.importValueSet(valueSet("vsvr-1", "1.0.0", ["c1"]), "vsvr-1")
    vsImport.importValueSet(valueSet("vsvr-2", "2.0.0", ["c1", "c2"]), "vsvr-2")

    expect: "each version expands to its own member set"
    expandCodes("1.0.0") == ["c1"] as Set
    expandCodes("2.0.0") == ["c1", "c2"] as Set
  }

  private Set<String> expandCodes(String version) {
    def req = new Parameters().setParameter([
        new ParametersParameter().setName("url").setValueUri(VS_URL),
        new ParametersParameter().setName("valueSetVersion").setValueString(version)])
    def vs = vsExpand.run(req)
    return (vs.expansion?.contains ?: []).collect { it.code } as Set
  }

  private static String baseCs() {
    FhirMapper.toJson([
        resourceType: "CodeSystem", id: "vsvr-cs", url: CS_URL, name: "VsvrCs", title: "Vsvr CS",
        status: "active", content: "complete", version: "1.0.0",
        concept: [[code: "c1", display: "C1"], [code: "c2", display: "C2"], [code: "c3", display: "C3"]]])
  }

  private static String valueSet(String id, String version, List<String> codes) {
    FhirMapper.toJson([
        resourceType: "ValueSet", id: id, url: VS_URL, name: "Vsvr", title: "Vsvr",
        status: "active", version: version,
        compose: [include: [[system: CS_URL, concept: codes.collect { [code: it] }]]]])
  }
}
