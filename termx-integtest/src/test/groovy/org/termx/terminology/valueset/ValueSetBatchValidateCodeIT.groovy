package org.termx.terminology.valueset

import com.kodality.kefhir.structure.api.ResourceContent
import com.kodality.zmei.fhir.FhirMapper
import com.kodality.zmei.fhir.resource.other.Parameters
import com.kodality.zmei.fhir.resource.other.Parameters.ParametersParameter
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import org.termx.TermxIntegTest
import org.termx.core.auth.SessionInfo
import org.termx.core.auth.SessionStore
import org.termx.terminology.fhir.codesystem.CodeSystemFhirImportService
import org.termx.terminology.fhir.valueset.operations.ValueSetBatchValidateCodeOperation

/**
 * ValueSet $batch-validate-code: many codes validated against value sets in one request. The value set is
 * supplied once as a shared inline tx-resource at the batch top level; each `validation` item carries its
 * own code/system. Responses come back one per item, in order.
 */
@MicronautTest(transactional = true)
class ValueSetBatchValidateCodeIT extends TermxIntegTest {
  @Inject CodeSystemFhirImportService csImport
  @Inject ValueSetBatchValidateCodeOperation vsBatch

  static final String CS_URL = "http://termx-test.local/CodeSystem/vsbatch-cs"
  static final String VS_URL = "urn:test:vsbatch-inline-vs"

  void setup() {
    SessionStore.setLocal(new SessionInfo().setPrivileges(["*.*.*"] as Set))
    csImport.importCodeSystem(FhirMapper.toJson([
        resourceType: "CodeSystem", id: "vsbatch-cs", url: CS_URL, name: "VsbatchCs", title: "Vsbatch CS",
        status: "active", content: "complete", version: "1.0.0",
        concept: [[code: "c1", display: "C1"], [code: "c2", display: "C2"], [code: "c3", display: "C3"]]]), "vsbatch-cs")
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

  private static ParametersParameter validation(String code) {
    new ParametersParameter().setName("validation").setResource(new Parameters().setParameter([
        new ParametersParameter().setName("url").setValueUri(VS_URL),
        new ParametersParameter().setName("code").setValueCode(code),
        new ParametersParameter().setName("system").setValueUri(CS_URL)]))
  }

  def "validates each code against the shared inline value set, preserving order"() {
    given:
    def batch = new Parameters().setParameter([inlineVs(["c1", "c2"]), validation("c1"), validation("c3"), validation("c2")])

    when:
    def content = vsBatch.run(new ResourceContent(FhirMapper.toJson(batch), "json"))
    def resp = FhirMapper.fromJson(content.getValue(), Parameters)
    def results = resp.getParameter().findAll { it.name == "validation" }.collect { it.resource as Parameters }

    then:
    results.size() == 3
    results[0].findParameter("result").orElseThrow().valueBoolean
    !results[1].findParameter("result").orElseThrow().valueBoolean
    results[2].findParameter("result").orElseThrow().valueBoolean
  }
}
