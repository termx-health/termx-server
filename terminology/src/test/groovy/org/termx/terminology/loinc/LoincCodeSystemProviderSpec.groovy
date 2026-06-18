package org.termx.terminology.loinc

import com.kodality.zmei.fhir.datatypes.Coding
import com.kodality.zmei.fhir.resource.other.Parameters
import com.kodality.zmei.fhir.resource.other.Parameters.ParametersParameter
import io.micronaut.context.BeanProvider
import org.termx.terminology.terminology.codesystem.CodeSystemService
import org.termx.ts.codesystem.CodeSystem
import org.termx.ts.codesystem.ConceptQueryParams
import org.termx.ts.codesystem.EntityPropertyType
import spock.lang.Specification

class LoincCodeSystemProviderSpec extends Specification {
  def loincClient = Mock(LoincClient)
  def codeSystemService = Mock(CodeSystemService)
  def beanProvider = Mock(BeanProvider) { get() >> codeSystemService }
  def provider = new LoincCodeSystemProvider(loincClient, beanProvider)

  private static Parameters lookupResponse() {
    new Parameters().setParameter([
        new ParametersParameter().setName("display").setValueString("Hematocrit, Blood"),
        new ParametersParameter().setName("designation").setPart([
            new ParametersParameter().setName("language").setValueCode("en"),
            new ParametersParameter().setName("value").setValueString("Hematocrit [Volume Fraction] of Blood"),
            new ParametersParameter().setName("use").setValueCoding(new Coding().setCode("FULLY_SPECIFIED_NAME"))]),
        new ParametersParameter().setName("property").setPart([
            new ParametersParameter().setName("code").setValueCode("CLASS"),
            new ParametersParameter().setName("value").setValueString("HEM/BC")]),
        new ParametersParameter().setName("property").setPart([
            new ParametersParameter().setName("code").setValueCode("STATUS"),
            new ParametersParameter().setName("value").setValueCode("ACTIVE")]),
    ])
  }

  def "delegates a loinc code lookup and maps display, designations and typed properties"() {
    given:
    provider.source = "local-first"
    loincClient.isConfigured() >> true
    codeSystemService.load("loinc") >> Optional.of(new CodeSystem().setContent("not-present"))
    loincClient.lookup("4544-3", null) >> lookupResponse()

    when:
    def result = provider.searchConcepts(new ConceptQueryParams().setCodeSystem("loinc").setCodeEq("4544-3"))
    def concept = result.data[0]
    def version = concept.versions[0]

    then:
    concept.code == "4544-3"
    concept.codeSystem == "loinc"
    version.designations.find { it.designationType == "display" }.name == "Hematocrit, Blood"
    version.designations.find { it.designationType == "FULLY_SPECIFIED_NAME" }.language == "en"
    version.propertyValues.find { it.entityProperty == "CLASS" }.with { it.value == "HEM/BC" && it.entityPropertyType == EntityPropertyType.string }
    version.propertyValues.find { it.entityProperty == "STATUS" }.entityPropertyType == EntityPropertyType.code
  }

  def "source=local never delegates"() {
    given:
    provider.source = "local"
    loincClient.isConfigured() >> true

    when:
    def result = provider.searchConcepts(new ConceptQueryParams().setCodeSystem("loinc").setCodeEq("4544-3"))

    then:
    result.data.isEmpty()
    0 * loincClient.lookup(_, _)
  }

  def "local-first defers to local when LOINC has local content"() {
    given:
    provider.source = "local-first"
    loincClient.isConfigured() >> true
    codeSystemService.load("loinc") >> Optional.of(new CodeSystem().setContent("complete"))

    when:
    def result = provider.searchConcepts(new ConceptQueryParams().setCodeSystem("loinc").setCodeEq("4544-3"))

    then:
    result.data.isEmpty()
    0 * loincClient.lookup(_, _)
  }

  def "external always delegates even with local content"() {
    given:
    provider.source = "external"
    loincClient.isConfigured() >> true
    loincClient.lookup("4544-3", null) >> lookupResponse()

    expect:
    provider.searchConcepts(new ConceptQueryParams().setCodeSystem("loinc").setCodeEq("4544-3")).data[0].code == "4544-3"
  }

  def "ignores non-loinc systems and unconfigured client"() {
    expect:
    provider.searchConcepts(new ConceptQueryParams().setCodeSystem("snomed-ct").setCodeEq("x")).data.isEmpty()
    provider.getCodeSystemId() == "loinc"
  }
}
