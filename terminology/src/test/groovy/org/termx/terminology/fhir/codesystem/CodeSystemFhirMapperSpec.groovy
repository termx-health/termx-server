package org.termx.terminology.fhir.codesystem

import com.kodality.commons.model.LocalizedName
import com.kodality.commons.model.QueryResult
import com.kodality.zmei.fhir.Extension
import org.termx.terminology.terminology.codesystem.CodeSystemService
import org.termx.terminology.terminology.codesystem.concept.ConceptService
import org.termx.terminology.terminology.mapset.MapSetService
import org.termx.terminology.terminology.relatedartifacts.CodeSystemRelatedArtifactService
import org.termx.terminology.terminology.valueset.ValueSetService
import org.termx.ts.PublicationStatus
import org.termx.ts.codesystem.CodeSystem
import org.termx.ts.codesystem.CodeSystemVersion
import org.termx.ts.codesystem.EntityProperty
import org.termx.ts.codesystem.EntityPropertyKind
import org.termx.ts.codesystem.EntityPropertyRule
import org.termx.ts.codesystem.EntityPropertyType
import org.termx.ts.valueset.ValueSet
import spock.lang.Specification

import java.time.LocalDate

class CodeSystemFhirMapperSpec extends Specification {
  def conceptService = Mock(ConceptService)
  def codeSystemService = Mock(CodeSystemService)
  def valueSetService = Mock(ValueSetService)
  def mapSetService = Mock(MapSetService)
  def relatedArtifactService = Mock(CodeSystemRelatedArtifactService)

  def mapper = new CodeSystemFhirMapper(conceptService, codeSystemService, valueSetService, mapSetService, relatedArtifactService)

  def "toFhir exports property binding extensions"() {
    given:
    conceptService.load(_, _) >> Optional.empty()
    def codeSystem = new CodeSystem()
        .setId("test-external")
        .setUri("http://fhir.ee/CodeSystem/test-external")
        .setName("test-external")
        .setTitle(new LocalizedName([en: "test-external"]))
        .setContent("not-present")
        .setProperties([
            new EntityProperty()
                .setName("coco")
                .setKind(EntityPropertyKind.property)
                .setType(EntityPropertyType.coding)
                .setRule(new EntityPropertyRule()
                    .setValueSet("vs-1")
                    .setCodeSystems(["cs-1", "cs-2"]))
        ])
    def version = new CodeSystemVersion()
        .setVersion("0.0.1")
        .setPreferredLanguage("en")
        .setReleaseDate(LocalDate.parse("2026-03-18"))
        .setStatus(PublicationStatus.draft)

    valueSetService.load("vs-1") >> new ValueSet().setId("vs-1").setUri("http://fhir.ee/ValueSet/vs-1")
    codeSystemService.load("cs-1") >> Optional.of(new CodeSystem().setId("cs-1").setUri("http://fhir.ee/CodeSystem/cs-1"))
    codeSystemService.load("cs-2") >> Optional.of(new CodeSystem().setId("cs-2").setUri("http://fhir.ee/CodeSystem/cs-2"))
    codeSystemService.query({ it.ids == "cs-1,cs-2" && it.limit == 2 }) >>
        new QueryResult<CodeSystem>([
            new CodeSystem().setId("cs-1").setUri("http://fhir.ee/CodeSystem/cs-1"),
            new CodeSystem().setId("cs-2").setUri("http://fhir.ee/CodeSystem/cs-2")
        ])

    when:
    def fhir = mapper.toFhir(codeSystem, version, [])
    def property = fhir.property.first()
    def valueSets = property.getExtensions("http://hl7.org/fhir/StructureDefinition/codesystem-property-valueset")
        .map { it.valueCanonical }
        .toList()
    def codeSystems = property.getExtensions("https://termx.org/fhir/StructureDefinition/codesystem-property-codesystem")
        .map { it.valueCanonical }
        .collect { it }
        .sort()

    then:
    property.code == "coco"
    property.type == EntityPropertyType.coding
    valueSets == ["http://fhir.ee/ValueSet/vs-1"]
    codeSystems == ["http://fhir.ee/CodeSystem/cs-1", "http://fhir.ee/CodeSystem/cs-2"]
  }

  def "fromFhir imports property binding extensions"() {
    given:
    def fhir = new com.kodality.zmei.fhir.resource.terminology.CodeSystem()
        .setId("test-external")
        .setUrl("http://fhir.ee/CodeSystem/test-external")
        .setName("test-external")
        .setTitle("test-external")
        .setContent("not-present")
        .setProperty([
            new com.kodality.zmei.fhir.resource.terminology.CodeSystem.CodeSystemProperty()
                .setCode("coco")
                .setType(EntityPropertyType.coding)
                .addExtension(new Extension("http://hl7.org/fhir/StructureDefinition/codesystem-property-valueset")
                    .setValueCanonical("http://fhir.ee/ValueSet/vs-1"))
                .addExtension(new Extension("https://termx.org/fhir/StructureDefinition/codesystem-property-codesystem")
                    .setValueCanonical("http://fhir.ee/CodeSystem/cs-1"))
                .addExtension(new Extension("https://termx.org/fhir/StructureDefinition/codesystem-property-codesystem")
                    .setValueCanonical("http://fhir.ee/CodeSystem/cs-2"))
        ])

    valueSetService.query({ it.uri == "http://fhir.ee/ValueSet/vs-1" && it.limit == 1 }) >>
        new QueryResult<ValueSet>([new ValueSet().setId("vs-1").setUri("http://fhir.ee/ValueSet/vs-1")])
    codeSystemService.query({ it.uri == "http://fhir.ee/CodeSystem/cs-1" && it.limit == 1 }) >>
        new QueryResult<CodeSystem>([new CodeSystem().setId("cs-1").setUri("http://fhir.ee/CodeSystem/cs-1")])
    codeSystemService.query({ it.uri == "http://fhir.ee/CodeSystem/cs-2" && it.limit == 1 }) >>
        new QueryResult<CodeSystem>([new CodeSystem().setId("cs-2").setUri("http://fhir.ee/CodeSystem/cs-2")])

    when:
    def imported = mapper.fromFhirCodeSystem(fhir)
    def property = imported.properties.find { it.name == "coco" }

    then:
    property != null
    property.type == EntityPropertyType.coding
    property.rule != null
    property.rule.valueSet == "vs-1"
    property.rule.codeSystems == ["cs-1", "cs-2"]
  }
}
