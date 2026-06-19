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
 * $expand with the FHIR {@code property} parameter: a requested concept property is surfaced in
 * contains[].property (and declared in expansion.property) even when the value set does not declare it.
 * The stored snapshot carries no property values, so they are loaded on demand; the inline (tx-resource)
 * path renders the same shape. Without the parameter no property is emitted.
 */
@MicronautTest(transactional = true)
class ValueSetExpandPropertyIT extends TermxIntegTest {
  @Inject CodeSystemFhirImportService csImportService
  @Inject ValueSetFhirImportService vsImportService
  @Inject ValueSetExpandOperation vsExpand

  void setup() {
    def sessionInfo = new SessionInfo()
    sessionInfo.privileges = ['*.*.*']
    SessionStore.setLocal(sessionInfo)
    csImportService.importCodeSystem(fixture("fhir/property/cs-property.json"), "prop-cs")
    vsImportService.importValueSet(fixture("fhir/property/vs-property.json"), "prop-vs")
  }

  void cleanup() {
    SessionStore.clearLocal()
  }

  def "the stored expand surfaces a requested property in contains[].property"() {
    when:
    def req = new Parameters().setParameter([
        new ParametersParameter().setName("url").setValueUri("http://example.org/prop-vs"),
        new ParametersParameter().setName("property").setValueCode("kind")])
    def expanded = vsExpand.run(req)

    then: "expansion.property declares kind and each member carries its kind value"
    expanded.expansion.property.collect { it.code } == ["kind"]
    def a = expanded.expansion.contains.find { it.code == "a" }
    a.property.find { it.code == "kind" }.valueString == "fruit"
    def c = expanded.expansion.contains.find { it.code == "c" }
    c.property.find { it.code == "kind" }.valueString == "veg"
  }

  def "no property parameter emits no contains[].property"() {
    when:
    def req = new Parameters().setParameter([
        new ParametersParameter().setName("url").setValueUri("http://example.org/prop-vs")])
    def a = vsExpand.run(req).expansion.contains.find { it.code == "a" }

    then:
    a.property == null || a.property.isEmpty()
  }

  def "the inline (tx-resource) expand path surfaces the requested property too"() {
    when:
    def req = com.kodality.zmei.fhir.FhirMapper.fromJson("""
      {"resourceType":"Parameters","parameter":[
        {"name":"property","valueCode":"kind"},
        {"name":"valueSet","resource":{
          "resourceType":"ValueSet","url":"http://example.org/prop-vs-inline","status":"active",
          "compose":{"include":[{"system":"http://example.org/prop-cs"}]}}}]}""", Parameters)
    def expanded = vsExpand.run(req)

    then: "the inline path declares and emits the property identically"
    expanded.expansion.property.collect { it.code } == ["kind"]
    expanded.expansion.contains.find { it.code == "a" }.property.find { it.code == "kind" }.valueString == "fruit"
  }

  private String fixture(String path) {
    def stream = getClass().getClassLoader().getResourceAsStream(path)
    assert stream != null, "fixture not found: ${path}"
    return new String(stream.readAllBytes()).replace("﻿", "")
  }
}
