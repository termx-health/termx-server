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
import org.termx.terminology.fhir.codesystem.operations.CodeSystemValidateCodeOperation

/**
 * tx-resource for CodeSystem $validate-code: the code system is supplied inline (a tx-resource whose url
 * matches the requested url/system, or an explicit codeSystem parameter), never stored. The code must be
 * validated against the inline definition rather than failing with "CodeSystem not found". This is the
 * shape the FHIR validator uses for code systems it carries itself (e.g. urn:iso:std:iso:3166).
 */
@MicronautTest(transactional = true)
class CodeSystemValidateCodeTxResourceIT extends TermxIntegTest {
  @Inject CodeSystemValidateCodeOperation csValidate

  static final String CS_URL = "urn:test:txcsvc"

  void setup() {
    SessionStore.setLocal(new SessionInfo().setPrivileges(["*.*.*"] as Set))
  }

  void cleanup() {
    SessionStore.clearLocal()
  }

  private static ParametersParameter inlineCs() {
    new ParametersParameter().setName("tx-resource").setResource(FhirMapper.fromJson(FhirMapper.toJson([
        resourceType: "CodeSystem", id: "txcsvc", url: CS_URL, name: "TxcsvcCs", status: "active",
        content: "complete", caseSensitive: true,
        concept: [[code: "c1", display: "C1"], [code: "c2", display: "C2"]]]),
        com.kodality.zmei.fhir.resource.terminology.CodeSystem))
  }

  private Parameters validate(List<ParametersParameter> params) {
    def content = csValidate.run(new ResourceContent(FhirMapper.toJson(new Parameters().setParameter(params)), "json"))
    FhirMapper.fromJson(content.getValue(), Parameters)
  }

  def "a code present in the inline tx-resource code system validates true"() {
    when:
    def resp = validate([
        new ParametersParameter().setName("url").setValueUri(CS_URL),
        new ParametersParameter().setName("code").setValueCode("c1"),
        inlineCs()])

    then:
    resp.findParameter("result").orElseThrow().valueBoolean
    resp.findParameter("display").orElseThrow().valueString == "C1"
  }

  def "a code absent from the inline tx-resource code system validates false"() {
    when:
    def resp = validate([
        new ParametersParameter().setName("url").setValueUri(CS_URL),
        new ParametersParameter().setName("code").setValueCode("c3"),
        inlineCs()])

    then:
    !resp.findParameter("result").orElseThrow().valueBoolean
    resp.findParameter("message").orElseThrow().valueString.contains("c3")
  }

  def "an incorrect display against an inline code is reported"() {
    when:
    def resp = validate([
        new ParametersParameter().setName("url").setValueUri(CS_URL),
        new ParametersParameter().setName("code").setValueCode("c1"),
        new ParametersParameter().setName("display").setValueString("Wrong"),
        inlineCs()])

    then:
    !resp.findParameter("result").orElseThrow().valueBoolean
    resp.findParameter("message").orElseThrow().valueString.contains("display")
  }

  def "without the tx-resource the same unstored url is not found"() {
    when:
    def resp = validate([
        new ParametersParameter().setName("url").setValueUri(CS_URL),
        new ParametersParameter().setName("code").setValueCode("c1")])

    then:
    !resp.findParameter("result").orElseThrow().valueBoolean
    resp.findParameter("message").orElseThrow().valueString.contains("not found")
  }
}
