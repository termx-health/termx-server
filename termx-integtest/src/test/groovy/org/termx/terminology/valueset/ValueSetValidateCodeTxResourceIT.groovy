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
import org.termx.terminology.fhir.valueset.operations.ValueSetValidateCodeOperation

/**
 * tx-resource for ValueSet $validate-code: the value set is supplied inline (a tx-resource whose url matches
 * the request url, or an explicit valueSet parameter), never stored. The code must be validated against the
 * inline value set's expansion (over its included, stored CodeSystem).
 */
@MicronautTest(transactional = true)
class ValueSetValidateCodeTxResourceIT extends TermxIntegTest {
  @Inject CodeSystemFhirImportService csImport
  @Inject ValueSetValidateCodeOperation vsValidate

  static final String CS_URL = "http://termx-test.local/CodeSystem/txvc-cs"
  static final String VS_URL = "urn:test:txvc-inline-vs"

  void setup() {
    SessionStore.setLocal(new SessionInfo().setPrivileges(["*.*.*"] as Set))
    csImport.importCodeSystem(FhirMapper.toJson([
        resourceType: "CodeSystem", id: "txvc-cs", url: CS_URL, name: "TxvcCs", title: "Txvc CS",
        status: "active", content: "complete", version: "1.0.0",
        concept: [[code: "c1", display: "C1"], [code: "c2", display: "C2"], [code: "c3", display: "C3"]]]), "txvc-cs")
  }

  void cleanup() {
    SessionStore.clearLocal()
  }

  private static ParametersParameter inlineVs(List<String> codes) {
    new ParametersParameter().setName("tx-resource").setResource(FhirMapper.fromJson(FhirMapper.toJson([
        resourceType: "ValueSet", url: VS_URL, status: "active",
        compose: [include: [[system: CS_URL, concept: codes.collect { [code: it] }]]]]),
        com.kodality.zmei.fhir.resource.terminology.ValueSet))
  }

  private Parameters validate(String code, List<String> vsCodes) {
    vsValidate.run(new Parameters().setParameter([
        new ParametersParameter().setName("url").setValueUri(VS_URL),
        new ParametersParameter().setName("code").setValueCode(code),
        new ParametersParameter().setName("system").setValueUri(CS_URL),
        inlineVs(vsCodes)]))
  }

  def "a code in the inline tx-resource value set validates true"() {
    expect:
    validate("c1", ["c1", "c2"]).findParameter("result").orElseThrow().valueBoolean
  }

  def "a code NOT in the inline tx-resource value set validates false"() {
    when:
    def resp = validate("c3", ["c1", "c2"])

    then: "result is false, the message reports the code is not found, and a structured issues OperationOutcome is returned"
    !resp.findParameter("result").orElseThrow().valueBoolean
    resp.findParameter("message").orElseThrow().valueString.contains("not found in the value set")
    def issue = resp.findParameter("issues").orElseThrow().resource.getIssue().first()
    issue.getDetails().getCoding().first().getCode() == "not-in-vs"
  }
}
