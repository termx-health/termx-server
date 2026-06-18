package org.termx.terminology.codesystem

import com.kodality.kefhir.structure.api.ResourceContent
import com.kodality.zmei.fhir.FhirMapper
import com.kodality.zmei.fhir.resource.other.Parameters
import com.kodality.zmei.fhir.resource.other.Parameters.ParametersParameter
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import org.termx.TermxIntegTest
import org.termx.core.auth.SessionInfo
import org.termx.core.auth.SessionStore
import org.termx.terminology.fhir.codesystem.operations.CodeSystemBatchValidateCodeOperation

/**
 * CodeSystem $batch-validate-code: many codes validated in one request. Each `validation` item carries its
 * own $validate-code params; shared inputs (here the inline tx-resource code system) sit at the batch top
 * level. The response holds one `validation` result per item, in order.
 */
@MicronautTest(transactional = true)
class CodeSystemBatchValidateCodeIT extends TermxIntegTest {
  @Inject CodeSystemBatchValidateCodeOperation csBatch

  static final String CS_URL = "urn:test:txcsbatch"

  void setup() {
    SessionStore.setLocal(new SessionInfo().setPrivileges(["*.*.*"] as Set))
  }

  void cleanup() {
    SessionStore.clearLocal()
  }

  private static ParametersParameter inlineCs() {
    new ParametersParameter().setName("tx-resource").setResource(FhirMapper.fromJson(FhirMapper.toJson([
        resourceType: "CodeSystem", id: "txcsbatch", url: CS_URL, name: "TxcsbatchCs", status: "active",
        content: "complete", caseSensitive: true,
        concept: [[code: "c1", display: "C1"], [code: "c2", display: "C2"]]]),
        com.kodality.zmei.fhir.resource.terminology.CodeSystem))
  }

  private static ParametersParameter validation(String code) {
    new ParametersParameter().setName("validation").setResource(new Parameters().setParameter([
        new ParametersParameter().setName("url").setValueUri(CS_URL),
        new ParametersParameter().setName("code").setValueCode(code)]))
  }

  def "validates each code against the shared inline code system, preserving order"() {
    given:
    def batch = new Parameters().setParameter([inlineCs(), validation("c1"), validation("c3"), validation("c2")])

    when:
    def content = csBatch.run(new ResourceContent(FhirMapper.toJson(batch), "json"))
    def resp = FhirMapper.fromJson(content.getValue(), Parameters)
    def results = resp.getParameter().findAll { it.name == "validation" }.collect { it.resource as Parameters }

    then:
    results.size() == 3
    results[0].findParameter("result").orElseThrow().valueBoolean
    !results[1].findParameter("result").orElseThrow().valueBoolean
    results[2].findParameter("result").orElseThrow().valueBoolean
    results[0].findParameter("display").orElseThrow().valueString == "C1"
  }
}
