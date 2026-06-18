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
import org.termx.terminology.fhir.valueset.operations.ValueSetExpandOperation

/**
 * tx-resource: the FHIR validator passes referenced resources inline so the server need not have them
 * stored. A {@code $expand} whose {@code url} names a ValueSet supplied as a {@code tx-resource} parameter
 * must expand that inline definition (resolving its included, stored CodeSystem) rather than 404.
 */
@MicronautTest(transactional = true)
class ValueSetExpandTxResourceIT extends TermxIntegTest {
  @Inject CodeSystemFhirImportService csImport
  @Inject ValueSetExpandOperation vsExpand

  static final String CS_URL = "http://termx-test.local/CodeSystem/txr-cs"
  static final String VS_URL = "http://termx-test.local/ValueSet/txr-vs"

  void setup() {
    SessionStore.setLocal(new SessionInfo().setPrivileges(["*.*.*"] as Set))
    csImport.importCodeSystem(FhirMapper.toJson([
        resourceType: "CodeSystem", id: "txr-cs", url: CS_URL, name: "TxrCs", title: "Txr CS",
        status: "active", content: "complete", version: "1.0.0",
        concept: [[code: "c1", display: "C1"], [code: "c2", display: "C2"]]]), "txr-cs")
  }

  void cleanup() {
    SessionStore.clearLocal()
  }

  def "\$expand resolves a url against an inline tx-resource ValueSet (never stored)"() {
    given: "an \$expand for a VS url that is NOT stored, with that VS supplied inline as a tx-resource"
    def inlineVs = [resourceType: "ValueSet", url: VS_URL, status: "active",
                    compose: [include: [[system: CS_URL, concept: [[code: "c1"]]]]]]
    def req = new Parameters().setParameter([
        new ParametersParameter().setName("url").setValueUri(VS_URL),
        new ParametersParameter().setName("tx-resource").setResource(FhirMapper.fromJson(FhirMapper.toJson(inlineVs), com.kodality.zmei.fhir.resource.terminology.ValueSet)),
    ])

    when:
    def expanded = vsExpand.run(req)

    then: "it expands the inline definition rather than failing to find the (unstored) value set"
    expanded.expansion.contains*.code as Set == ["c1"] as Set
  }

  def "without the tx-resource the same unstored url is not found"() {
    when:
    vsExpand.run(new Parameters().setParameter([new ParametersParameter().setName("url").setValueUri(VS_URL)]))

    then:
    thrown(Exception)
  }
}
