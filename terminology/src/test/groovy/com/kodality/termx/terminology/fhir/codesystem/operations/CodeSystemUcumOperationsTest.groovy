package com.kodality.termx.terminology.fhir.codesystem.operations

import com.kodality.commons.model.QueryResult
import com.kodality.kefhir.structure.api.ResourceContent
import com.kodality.termx.core.auth.SessionInfo
import com.kodality.termx.core.auth.SessionStore
import com.kodality.termx.terminology.terminology.codesystem.CodeSystemService
import com.kodality.termx.terminology.terminology.codesystem.concept.ConceptService
import com.kodality.termx.terminology.terminology.codesystem.version.CodeSystemVersionService
import com.kodality.termx.ts.codesystem.CodeSystem
import com.kodality.termx.ts.codesystem.CodeSystemEntityVersion
import com.kodality.termx.ts.codesystem.CodeSystemQueryParams
import com.kodality.termx.ts.codesystem.Concept
import com.kodality.termx.ts.codesystem.ConceptQueryParams
import com.kodality.termx.ts.codesystem.Designation
import com.kodality.zmei.fhir.FhirMapper
import com.kodality.zmei.fhir.datatypes.Coding
import com.kodality.zmei.fhir.resource.other.Parameters
import spock.lang.Specification

class CodeSystemUcumOperationsTest extends Specification {
  def conceptService = Mock(ConceptService)
  def codeSystemService = Mock(CodeSystemService)
  def codeSystemVersionService = Mock(CodeSystemVersionService)

  def validateCodeOperation = new CodeSystemValidateCodeOperation(conceptService, codeSystemService, codeSystemVersionService)
  def lookupOperation = new CodeSystemLookupOperation(conceptService, codeSystemService)

  def setup() {
    SessionStore.setLocal(new SessionInfo().setPrivileges(["*.*.*"] as Set))
  }

  def cleanup() {
    SessionStore.clearLocal()
  }

  def "validate-code accepts UCUM supplement abbreviation as display"() {
    given:
    codeSystemService.query(_ as CodeSystemQueryParams) >> { args ->
      CodeSystemQueryParams p = args[0]
      if (p.uri == "http://unitsofmeasure.org") {
        return new QueryResult([new CodeSystem().setId("ucum").setUri("http://unitsofmeasure.org")])
      }
      if (p.uri == "https://termx.org/fhir/CodeSystem/ucum-supplement-lt") {
        return new QueryResult([new CodeSystem().setId("ucum-supplement-lt").setBaseCodeSystem("ucum")])
      }
      return QueryResult.empty()
    }
    conceptService.query(_ as ConceptQueryParams) >> { args ->
      ConceptQueryParams p = args[0]
      if (p.codeSystem == "ucum") {
        return new QueryResult([concept("mL", [
            designation("display", "en", "milliliter")
        ])])
      }
      if (p.codeSystem == "ucum-supplement-lt") {
        return new QueryResult([concept("mL", [
            designation("abbreviation", "lt", "ml"),
            designation("definition", "lt", "Milliliter")
        ])])
      }
      return QueryResult.empty()
    }

    def request = new Parameters()
        .addParameter(new Parameters.ParametersParameter("url").setValueUrl("http://unitsofmeasure.org"))
        .addParameter(new Parameters.ParametersParameter("code").setValueCode("mL"))
        .addParameter(new Parameters.ParametersParameter("display").setValueString("ml"))
        .addParameter(new Parameters.ParametersParameter("displayLanguage").setValueCode("lt"))
        .addParameter(new Parameters.ParametersParameter("useSupplement").setValueCanonical("https://termx.org/fhir/CodeSystem/ucum-supplement-lt"))

    when:
    def response = validateCodeOperation.run(new ResourceContent(FhirMapper.toJson(request), "json"))
    def parameters = FhirMapper.fromJson(response.value, Parameters)

    then:
    parameters.findParameter("result").orElseThrow().valueBoolean
  }

  def "lookup returns UCUM supplement abbreviation and definition designations"() {
    given:
    codeSystemService.query(_ as CodeSystemQueryParams) >> { args ->
      CodeSystemQueryParams p = args[0]
      if (p.uri == "http://unitsofmeasure.org") {
        return new QueryResult([new CodeSystem().setId("ucum").setUri("http://unitsofmeasure.org")])
      }
      if (p.uri == "https://termx.org/fhir/CodeSystem/ucum-supplement-lt") {
        return new QueryResult([new CodeSystem().setId("ucum-supplement-lt").setBaseCodeSystem("ucum")])
      }
      return QueryResult.empty()
    }
    conceptService.query(_ as ConceptQueryParams) >> { args ->
      ConceptQueryParams p = args[0]
      if (p.codeSystem == "ucum") {
        return new QueryResult([concept("mL", [
            designation("display", "en", "milliliter")
        ])])
      }
      if (p.codeSystem == "ucum-supplement-lt") {
        return new QueryResult([concept("mL", [
            designation("abbreviation", "lt", "ml"),
            designation("definition", "lt", "Milliliter")
        ])])
      }
      return QueryResult.empty()
    }

    def request = new Parameters()
        .addParameter(new Parameters.ParametersParameter("system").setValueUri("http://unitsofmeasure.org"))
        .addParameter(new Parameters.ParametersParameter("code").setValueCode("mL"))
        .addParameter(new Parameters.ParametersParameter("useSupplement").setValueCanonical("https://termx.org/fhir/CodeSystem/ucum-supplement-lt"))

    when:
    def response = lookupOperation.run(new ResourceContent(FhirMapper.toJson(request), "json"))
    def parameters = FhirMapper.fromJson(response.value, Parameters)
    def designations = parameters.parameter.findAll { it.name == "designation" }
    def valuesByUse = designations.collectEntries {
      [(it.part.find { p -> p.name == "use" }?.valueCoding?.code): it.part.find { p -> p.name == "value" }?.valueString]
    }

    then:
    valuesByUse["abbreviation"] == "ml"
    valuesByUse["definition"] == "Milliliter"
  }

  def "lookup auto-loads supplements by displayLanguage"() {
    given:
    codeSystemService.query(_ as CodeSystemQueryParams) >> { args ->
      CodeSystemQueryParams p = args[0]
      if (p.uri == "http://unitsofmeasure.org") {
        return new QueryResult([new CodeSystem().setId("ucum").setUri("http://unitsofmeasure.org")])
      }
      if (p.baseCodeSystem == "ucum" && p.content == "supplement") {
        return new QueryResult([new CodeSystem()
            .setId("ucum-supplement-lt")
            .setUri("https://termx.org/fhir/CodeSystem/ucum-supplement-lt")
            .setBaseCodeSystem("ucum")])
      }
      if (p.uri == "https://termx.org/fhir/CodeSystem/ucum-supplement-lt") {
        return new QueryResult([new CodeSystem().setId("ucum-supplement-lt").setBaseCodeSystem("ucum")])
      }
      return QueryResult.empty()
    }
    conceptService.query(_ as ConceptQueryParams) >> { args ->
      ConceptQueryParams p = args[0]
      if (p.codeSystem == "ucum") {
        return new QueryResult([concept("mL", [
            designation("display", "en", "milliliter")
        ])])
      }
      if (p.codeSystem == "ucum-supplement-lt") {
        return new QueryResult([concept("mL", [
            designation("abbreviation", "lt", "ml"),
            designation("definition", "lt", "Milliliter")
        ])])
      }
      return QueryResult.empty()
    }

    def request = new Parameters()
        .addParameter(new Parameters.ParametersParameter("system").setValueUri("http://unitsofmeasure.org"))
        .addParameter(new Parameters.ParametersParameter("code").setValueCode("mL"))
        .addParameter(new Parameters.ParametersParameter("displayLanguage").setValueCode("lt"))

    when:
    def response = lookupOperation.run(new ResourceContent(FhirMapper.toJson(request), "json"))
    def parameters = FhirMapper.fromJson(response.value, Parameters)
    def designations = parameters.parameter.findAll { it.name == "designation" }
    def valuesByUse = designations.collectEntries {
      [(it.part.find { p -> p.name == "use" }?.valueCoding?.code): it.part.find { p -> p.name == "value" }?.valueString]
    }
    def designationLanguages = designations.collect { it.part.find { p -> p.name == "language" }?.valueString }.toSet()

    then:
    parameters.findParameter("display").orElseThrow().valueString == "milliliter"
    valuesByUse["abbreviation"] == "ml"
    valuesByUse["definition"] == "Milliliter"
    designationLanguages == ["lt"] as Set
  }

  private static Concept concept(String code, List<Designation> designations) {
    return new Concept()
        .setCode(code)
        .setCodeSystem("ucum")
        .setVersions([new CodeSystemEntityVersion().setCode(code).setCodeSystem("ucum").setDesignations(designations)])
  }

  private static Designation designation(String type, String language, String value) {
    return new Designation()
        .setDesignationType(type)
        .setLanguage(language)
        .setName(value)
        .setPreferred("display".equals(type))
        .setStatus("active")
  }
}
