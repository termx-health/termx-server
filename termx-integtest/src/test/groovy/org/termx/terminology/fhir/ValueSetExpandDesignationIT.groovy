package org.termx.terminology.fhir

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
 * $expand with the FHIR {@code designation} parameter: when present (0..*), each token (a language, or a
 * {@code system|code} use/language) restricts which designations appear in {@code contains[].designation};
 * absent, every designation is returned.
 */
@MicronautTest(transactional = true)
class ValueSetExpandDesignationIT extends TermxIntegTest {
  @Inject CodeSystemFhirImportService csImportService
  @Inject ValueSetFhirImportService vsImportService
  @Inject ValueSetExpandOperation vsExpand

  void setup() {
    def sessionInfo = new SessionInfo()
    sessionInfo.privileges = ['*.*.*']
    SessionStore.setLocal(sessionInfo)
    csImportService.importCodeSystem(fixture("fhir/designation/cs-designations.json"), "dg-cs")
    vsImportService.importValueSet(fixture("fhir/designation/vs-designations.json"), "dg-vs")
  }

  void cleanup() {
    SessionStore.clearLocal()
  }

  private List<String> expandDesignationLanguages(ParametersParameter... extra) {
    def params = [
        new ParametersParameter().setName("url").setValueUri("http://example.org/dg-vs"),
        new ParametersParameter().setName("includeDesignations").setValueBoolean(true)]
    params.addAll(extra)
    def contains = vsExpand.run(new Parameters().setParameter(params)).expansion.contains.find { it.code == "c1" }
    contains.designation.collect { it.language }
  }

  def "includeDesignations with no designation filter returns every designation"() {
    expect:
    expandDesignationLanguages().toSorted() == ["en", "en", "et", "ru"]
  }

  def "the designation parameter restricts the returned designations to the named language"() {
    expect:
    expandDesignationLanguages(new ParametersParameter().setName("designation").setValueCode("et")) == ["et"]
  }

  def "a system|code language designation token restricts by language too"() {
    expect:
    expandDesignationLanguages(new ParametersParameter().setName("designation").setValueString("urn:ietf:bcp:47|ru")) == ["ru"]
  }

  def "multiple designation tokens union the matching designations"() {
    expect:
    expandDesignationLanguages(
        new ParametersParameter().setName("designation").setValueCode("et"),
        new ParametersParameter().setName("designation").setValueCode("ru")).toSorted() == ["et", "ru"]
  }

  def "the designation filter also applies to the inline (tx-resource) expand path"() {
    given: "an INLINE value set (passed in the request) over the same base, restricted to et designations"
    def req = com.kodality.zmei.fhir.FhirMapper.fromJson("""
      {"resourceType":"Parameters","parameter":[
        {"name":"includeDesignations","valueBoolean":true},
        {"name":"designation","valueCode":"et"},
        {"name":"valueSet","resource":{
          "resourceType":"ValueSet","url":"http://example.org/dg-vs-inline","status":"active",
          "compose":{"include":[{"system":"http://example.org/dg-cs"}]}}}]}""", Parameters)

    when:
    def contains = vsExpand.run(req).expansion.contains.find { it.code == "c1" }

    then: "only the Estonian designation is returned on the inline path too"
    contains != null
    contains.designation.collect { it.language } == ["et"]
  }

  private String fixture(String path) {
    def stream = getClass().getClassLoader().getResourceAsStream(path)
    assert stream != null, "fixture not found: ${path}"
    return new String(stream.readAllBytes()).replace("﻿", "")
  }
}
