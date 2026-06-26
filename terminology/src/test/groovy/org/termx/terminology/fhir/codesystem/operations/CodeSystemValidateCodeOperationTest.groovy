package org.termx.terminology.fhir.codesystem.operations

import com.kodality.commons.model.QueryResult
import com.kodality.kefhir.structure.api.ResourceContent
import com.kodality.zmei.fhir.FhirMapper
import com.kodality.zmei.fhir.resource.other.Parameters
import com.kodality.zmei.fhir.resource.other.Parameters.ParametersParameter
import org.termx.core.auth.SessionInfo
import org.termx.core.auth.SessionStore
import org.termx.terminology.terminology.codesystem.CodeSystemService
import org.termx.terminology.terminology.codesystem.concept.ConceptService
import org.termx.terminology.terminology.codesystem.version.CodeSystemVersionService
import org.termx.ts.codesystem.CodeSystem
import org.termx.ts.codesystem.CodeSystemVersion
import org.termx.ts.codesystem.Concept
import spock.lang.Specification

/**
 * Behaviour tests for {@link CodeSystemValidateCodeOperation} version handling. A `version` that
 * does not resolve to a stored TermX CodeSystemVersion (e.g. a SNOMED edition URI) must be passed
 * through to the concept query as `codeSystemVersion` so external providers can derive their branch,
 * rather than hard-failing with "CodeSystem active version not found".
 */
class CodeSystemValidateCodeOperationTest extends Specification {
  def conceptService = Mock(ConceptService)
  def codeSystemService = Mock(CodeSystemService)
  def codeSystemVersionService = Mock(CodeSystemVersionService)

  def operation = new CodeSystemValidateCodeOperation(conceptService, codeSystemService, codeSystemVersionService)

  def setup() {
    SessionStore.setLocal(new SessionInfo().setPrivileges(["*.*.*"] as Set))
    codeSystemService.query(_) >> new QueryResult([new CodeSystem().setId("snomed-ct")])
    codeSystemService.load(_) >> Optional.of(new CodeSystem().setId("snomed-ct").setUri("http://snomed.info/sct").setName("SNOMED CT"))
  }

  def cleanup() {
    SessionStore.clearLocal()
  }

  def "passes a SNOMED edition URI version through as codeSystemVersion when it is not a stored version"() {
    given:
    codeSystemVersionService.query(_) >> new QueryResult([])

    when:
    def resp = parse(invoke([url: "http://snomed.info/sct", version: "http://snomed.info/sct/11000181102", code: "404684003"]))

    then: "the version URI reaches the concept query (so the SNOMED provider can branch); no internal version id"
    1 * conceptService.query({
      it.codeSystem == "snomed-ct" &&
          it.codeSystemVersion == "http://snomed.info/sct/11000181102" &&
          it.codeSystemVersionId == null &&
          it.code == "404684003"
    }) >> new QueryResult([new Concept().setCode("404684003")])
    bool(resp, "result")
  }

  def "validates without a version"() {
    when:
    def resp = parse(invoke([url: "http://snomed.info/sct", code: "404684003"]))

    then:
    0 * codeSystemVersionService.query(_)
    1 * conceptService.query({ it.codeSystemVersion == null && it.codeSystemVersionId == null }) >> new QueryResult([new Concept()])
    bool(resp, "result")
  }

  def "uses the internal version id when the version resolves to a stored TermX version"() {
    given:
    codeSystemVersionService.query(_) >> new QueryResult([new CodeSystemVersion().setId(42L)])

    when:
    def resp = parse(invoke([url: "http://snomed.info/sct", version: "1.0.0", code: "404684003"]))

    then: "the stored version id is used, and the raw version string is not forwarded"
    1 * conceptService.query({ it.codeSystemVersionId == 42L && it.codeSystemVersion == null }) >> new QueryResult([new Concept()])
    bool(resp, "result")
  }

  def "returns invalid code when the concept is not found"() {
    given:
    codeSystemVersionService.query(_) >> new QueryResult([])

    when:
    def resp = parse(invoke([url: "http://snomed.info/sct", version: "http://snomed.info/sct/11000181102", code: "000"]))

    then:
    1 * conceptService.query(_) >> new QueryResult([])
    !bool(resp, "result")
  }

  def "a coding naming a supplement code system degrades to a 200 invalid-data, not a 400"() {
    // A url-less $validate-code whose coding.system is a (bundled) supplement code system must not 400 ('url
    // parameter required' / supplement rejected) — the reference returns 200 result=false with an invalid-data issue.
    given:
    def supplement = new com.kodality.zmei.fhir.resource.terminology.CodeSystem()
    supplement.setUrl("http://hl7.org/fhir/test/CodeSystem/supplement")
    supplement.setVersion("0.1.1")
    supplement.setContent("supplement")
    def req = new Parameters()
        .addParameter(new ParametersParameter("coding").setValueCoding(
            new com.kodality.zmei.fhir.datatypes.Coding("http://hl7.org/fhir/test/CodeSystem/supplement", "code1")))
        .addParameter(new ParametersParameter("tx-resource").setResource(supplement))

    when:
    def resp = parse(operation.run(new ResourceContent(FhirMapper.toJson(req), "json")))

    then:
    !bool(resp, "result")
    resp.findParameter("code").get().valueCode == "code1"
    resp.findParameter("system").get().valueUri == "http://hl7.org/fhir/test/CodeSystem/supplement"
    def oo = resp.findParameter("issues").get().resource as com.kodality.zmei.fhir.resource.other.OperationOutcome
    oo.issue.size() == 1
    oo.issue[0].code == "invalid"
    oo.issue[0].details.coding[0].code == "invalid-data"
    oo.issue[0].details.text.contains("is a supplement, so can't be used as a value in Coding.system")
    oo.issue[0].location == ["Coding.system"]
  }

  private ResourceContent invoke(Map<String, String> params) {
    Parameters req = new Parameters()
    params.each { k, v -> req.addParameter(new ParametersParameter(k).setValueString(v)) }
    return operation.run(new ResourceContent(FhirMapper.toJson(req), "json"))
  }

  private static Parameters parse(ResourceContent rc) {
    return FhirMapper.fromJson(rc.getValue(), Parameters)
  }

  private static boolean bool(Parameters resp, String name) {
    return resp.findParameter(name).map(p -> p.getValueBoolean()).orElse(false)
  }
}
