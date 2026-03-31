package org.termx.terminology.fhir.codesystem.operations

import com.kodality.commons.model.QueryResult
import com.kodality.kefhir.structure.api.ResourceContent
import org.termx.core.auth.SessionInfo
import org.termx.core.auth.SessionStore
import org.termx.terminology.terminology.codesystem.CodeSystemService
import org.termx.terminology.terminology.codesystem.concept.ConceptService
import org.termx.terminology.terminology.codesystem.version.CodeSystemVersionService
import org.termx.ts.codesystem.CodeSystem
import org.termx.ts.codesystem.CodeSystemEntityVersion
import org.termx.ts.codesystem.CodeSystemQueryParams
import org.termx.ts.codesystem.Concept
import org.termx.ts.codesystem.ConceptQueryParams
import org.termx.ts.codesystem.ConceptSnapshot
import org.termx.ts.codesystem.Designation
import org.termx.ts.codesystem.EntityPropertyType
import org.termx.ts.codesystem.EntityPropertyValue
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

  def "lookup returns all properties by default when mode is ALL"() {
    given:
    def lookupOperation = new CodeSystemLookupOperation(conceptService, codeSystemService, LookupDefaultPropertyMode.ALL)
    codeSystemService.query(_ as CodeSystemQueryParams) >> new QueryResult([new CodeSystem().setId("ucum").setUri("http://unitsofmeasure.org")])
    conceptService.query(_ as ConceptQueryParams) >> new QueryResult([concept("mL", [
        designation("display", "en", "milliliter")
    ], [
        property("status", EntityPropertyType.code, "active"),
        property("comment", EntityPropertyType.string, "metric")
    ])])

    def request = new Parameters()
        .addParameter(new Parameters.ParametersParameter("system").setValueUri("http://unitsofmeasure.org"))
        .addParameter(new Parameters.ParametersParameter("code").setValueCode("mL"))

    when:
    def response = lookupOperation.run(new ResourceContent(FhirMapper.toJson(request), "json"))
    def parameters = FhirMapper.fromJson(response.value, Parameters)
    def properties = parameters.parameter.findAll { it.name == "property" }
    def propertyCodes = properties.collect { property -> property.part.find { it.name == "code" }?.valueString }.toSet()

    then:
    propertyCodes == ["status", "comment"] as Set
  }

  def "lookup returns coding property display from snapshot"() {
    given:
    codeSystemService.query(_ as CodeSystemQueryParams) >> new QueryResult([new CodeSystem().setId("ucum").setUri("http://unitsofmeasure.org")])
    conceptService.query(_ as ConceptQueryParams) >> new QueryResult([concept("16542-3", [
        designation("display", "en", "example")
    ], [
        property("expected-result-type", EntityPropertyType.coding, new Concept().setCodeSystem("lt-lab-test-results").setCode("KIEKYBINIS"))
    ], new ConceptSnapshot().setProperties([
        new ConceptSnapshot.SnapshotProperty(
            "expected-result-type",
            new ConceptSnapshot.SnapshotCoding("lt-lab-test-results", "1.0.0", "KIEKYBINIS", '[{"language":"en","name":"Quantitative"}]'),
            null,
            null)
    ]) )])

    def request = new Parameters()
        .addParameter(new Parameters.ParametersParameter("system").setValueUri("http://unitsofmeasure.org"))
        .addParameter(new Parameters.ParametersParameter("code").setValueCode("16542-3"))
        .addParameter(new Parameters.ParametersParameter("property").setValueCode("expected-result-type"))

    when:
    def response = lookupOperation.run(new ResourceContent(FhirMapper.toJson(request), "json"))
    def parameters = FhirMapper.fromJson(response.value, Parameters)
    def property = parameters.parameter.find { it.name == "property" }
    def coding = property.part.find { it.name == "value" }?.valueCoding

    then:
    coding.system == "lt-lab-test-results"
    coding.code == "KIEKYBINIS"
    coding.display == "Quantitative"
    coding.version == "1.0.0"
  }

  def "lookup returns no properties by default when mode is NONE unless explicitly requested"() {
    given:
    def lookupOperation = new CodeSystemLookupOperation(conceptService, codeSystemService, LookupDefaultPropertyMode.NONE)
    codeSystemService.query(_ as CodeSystemQueryParams) >> new QueryResult([new CodeSystem().setId("ucum").setUri("http://unitsofmeasure.org")])
    conceptService.query(_ as ConceptQueryParams) >> new QueryResult([concept("mL", [
        designation("display", "en", "milliliter")
    ], [
        property("status", EntityPropertyType.code, "active"),
        property("comment", EntityPropertyType.string, "metric")
    ])])

    def requestWithoutProperty = new Parameters()
        .addParameter(new Parameters.ParametersParameter("system").setValueUri("http://unitsofmeasure.org"))
        .addParameter(new Parameters.ParametersParameter("code").setValueCode("mL"))
    def requestWithProperty = new Parameters()
        .addParameter(new Parameters.ParametersParameter("system").setValueUri("http://unitsofmeasure.org"))
        .addParameter(new Parameters.ParametersParameter("code").setValueCode("mL"))
        .addParameter(new Parameters.ParametersParameter("property").setValueCode("status"))

    when:
    def responseWithoutProperty = lookupOperation.run(new ResourceContent(FhirMapper.toJson(requestWithoutProperty), "json"))
    def parametersWithoutProperty = FhirMapper.fromJson(responseWithoutProperty.value, Parameters)
    def responseWithProperty = lookupOperation.run(new ResourceContent(FhirMapper.toJson(requestWithProperty), "json"))
    def parametersWithProperty = FhirMapper.fromJson(responseWithProperty.value, Parameters)

    then:
    parametersWithoutProperty.parameter.findAll { it.name == "property" }.isEmpty()
    parametersWithProperty.parameter.findAll { it.name == "property" }.size() == 1
    parametersWithProperty.parameter.find { it.name == "property" }.part.find { it.name == "code" }.valueString == "status"
  }

  private static Concept concept(String code, List<Designation> designations, List<EntityPropertyValue> propertyValues = [], ConceptSnapshot snapshot = null) {
    return new Concept()
        .setCode(code)
        .setCodeSystem("ucum")
        .setVersions([new CodeSystemEntityVersion()
            .setCode(code)
            .setCodeSystem("ucum")
            .setDesignations(designations)
            .setSnapshot(snapshot)
            .setPropertyValues(propertyValues)])
  }

  private static Designation designation(String type, String language, String value) {
    return new Designation()
        .setDesignationType(type)
        .setLanguage(language)
        .setName(value)
        .setPreferred("display".equals(type))
        .setStatus("active")
  }

  private static EntityPropertyValue property(String code, String type, Object value) {
    return new EntityPropertyValue()
        .setEntityProperty(code)
        .setEntityPropertyType(type)
        .setValue(value)
  }
}
