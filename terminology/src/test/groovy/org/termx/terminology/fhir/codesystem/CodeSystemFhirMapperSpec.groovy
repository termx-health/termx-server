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

  def "toFhir exports supported-language extensions from supportedLanguages"() {
    given:
    conceptService.load(_, _) >> Optional.empty()
    def codeSystem = new CodeSystem().setId("cs").setUri("http://fhir.ee/CodeSystem/cs").setName("cs")
        .setTitle(new LocalizedName([en: "cs"])).setContent("not-present")
    def version = new CodeSystemVersion().setVersion("1.0.0").setPreferredLanguage("en")
        .setReleaseDate(LocalDate.parse("2026-03-18")).setStatus(PublicationStatus.draft)
        .setSupportedLanguages(["et", "en"])

    when:
    def fhir = mapper.toFhir(codeSystem, version, [])
    def languages = fhir.extension.findAll { it.url == "https://termx.org/fhir/StructureDefinition/supported-language" }.collect { it.valueCode }

    then:
    languages == ["et", "en"]
  }

  def "fromFhir imports supported-language extensions into the version supportedLanguages"() {
    given:
    def fhir = new com.kodality.zmei.fhir.resource.terminology.CodeSystem()
        .setId("cs").setUrl("http://fhir.ee/CodeSystem/cs").setName("cs").setTitle("cs").setContent("not-present")
        .addExtension(new Extension("https://termx.org/fhir/StructureDefinition/supported-language").setValueCode("et"))
        .addExtension(new Extension("https://termx.org/fhir/StructureDefinition/supported-language").setValueCode("ru"))

    when:
    def imported = mapper.fromFhirCodeSystem(fhir)
    def languages = imported.versions.first().supportedLanguages

    then:
    languages.contains("et")
    languages.contains("ru")
  }

  def "meta.profile round-trips: import populates profile, export emits meta.profile"() {
    given:
    conceptService.load(_, _) >> Optional.empty()
    def fhir = new com.kodality.zmei.fhir.resource.terminology.CodeSystem()
        .setId("cs").setUrl("http://fhir.ee/CodeSystem/cs").setName("cs").setContent("not-present")
    fhir.setMeta(new com.kodality.zmei.fhir.resource.Meta().setProfile([
        org.termx.ts.FhirProfile.SHAREABLE_CODE_SYSTEM, "http://example.org/StructureDefinition/custom"]))

    when: "import"
    def imported = mapper.fromFhirCodeSystem(fhir)

    then: "both the recognized and the custom profile are stored verbatim"
    imported.profile == [org.termx.ts.FhirProfile.SHAREABLE_CODE_SYSTEM, "http://example.org/StructureDefinition/custom"]

    when: "export the stored profile back out"
    def version = new CodeSystemVersion().setVersion("1.0.0").setPreferredLanguage("en")
        .setReleaseDate(LocalDate.parse("2026-06-18")).setStatus(PublicationStatus.draft)
    def out = mapper.toFhir(imported.setTitle(new LocalizedName([en: "cs"])), version, [])

    then:
    out.meta.profile == [org.termx.ts.FhirProfile.SHAREABLE_CODE_SYSTEM, "http://example.org/StructureDefinition/custom"]
  }

  def "no declared profile leaves meta unset on export"() {
    given:
    conceptService.load(_, _) >> Optional.empty()
    def cs = new CodeSystem().setId("cs").setUri("http://fhir.ee/CodeSystem/cs").setName("cs")
        .setTitle(new LocalizedName([en: "cs"])).setContent("not-present")
    def version = new CodeSystemVersion().setVersion("1.0.0").setPreferredLanguage("en")
        .setReleaseDate(LocalDate.parse("2026-06-18")).setStatus(PublicationStatus.draft)

    expect:
    mapper.toFhir(cs, version, []).meta == null
  }

  def "fromFhir defaults an absent title and name to the id (derived from the url's last segment)"() {
    given: "a CodeSystem with neither title nor name nor id — only a url (as many tx-ecosystem fixtures are)"
    def fhir = new com.kodality.zmei.fhir.resource.terminology.CodeSystem()
        .setUrl("http://hl7.org/fhir/test/CodeSystem/search")
        .setStatus("active")
        .setContent("complete")
        .setConcept([new com.kodality.zmei.fhir.resource.terminology.CodeSystem.CodeSystemConcept().setCode("a")])

    when:
    def cs = mapper.fromFhirCodeSystem(fhir)

    then: "title is non-null (TermX stores it NOT NULL) and falls back through name -> id -> url last segment"
    cs.id == "search"
    cs.name == "search"
    cs.title != null
    cs.title.values().contains("search")
    and: "the version's code_system FK uses the derived id (else the version insert hits a not-null violation)"
    cs.versions.first().codeSystem == "search"
    and: "concept and its entity version also carry the derived code_system id (their FKs are NOT NULL too)"
    cs.concepts.first().codeSystem == "search"
    cs.concepts.first().versions.first().codeSystem == "search"
  }

  def "fromFhir keeps an explicit title and only defaults the missing name"() {
    given:
    def fhir = new com.kodality.zmei.fhir.resource.terminology.CodeSystem()
        .setUrl("http://hl7.org/fhir/test/CodeSystem/simple")
        .setTitle("Simple CS")
        .setStatus("active")
        .setContent("complete")

    when:
    def cs = mapper.fromFhirCodeSystem(fhir)

    then:
    cs.name == "simple"
    cs.title.values().contains("Simple CS")
  }

  def "a SNOMED-url code system derives the concept display: preferred > FSN > synonym"() {
    given:
    def fhir = com.kodality.zmei.fhir.FhirMapper.fromJson('''
      {"resourceType":"CodeSystem","url":"http://snomed.info/sct","status":"active","content":"complete","language":"en","concept":[
        {"code":"100","designation":[
          {"use":{"system":"http://snomed.info/sct","code":"900000000000003001"},"language":"en","value":"Foo (finding)"},
          {"use":{"system":"http://snomed.info/sct","code":"900000000000013009"},"language":"en","value":"Foo"},
          {"use":{"system":"http://snomed.info/sct","code":"900000000000548007"},"language":"en","value":"Foo preferred"}]},
        {"code":"200","designation":[
          {"use":{"system":"http://snomed.info/sct","code":"900000000000003001"},"language":"en","value":"Bar (finding)"}]},
        {"code":"300","designation":[
          {"use":{"system":"http://snomed.info/sct","code":"900000000000013009"},"language":"en","value":"Baz syn"}]}]}''',
        com.kodality.zmei.fhir.resource.terminology.CodeSystem)

    when:
    def cs = mapper.fromFhirCodeSystem(fhir)

    then: "preferred wins; else FSN; else synonym"
    displayOf(cs, "100") == "Foo preferred"
    displayOf(cs, "200") == "Bar (finding)"
    displayOf(cs, "300") == "Baz syn"
  }

  def "a non-SNOMED code system does NOT derive a display from SNOMED designations"() {
    given:
    def fhir = com.kodality.zmei.fhir.FhirMapper.fromJson('''
      {"resourceType":"CodeSystem","url":"http://example.org/x","status":"active","content":"complete","language":"en","concept":[
        {"code":"a","designation":[
          {"use":{"system":"http://snomed.info/sct","code":"900000000000013009"},"language":"en","value":"A syn"}]}]}''',
        com.kodality.zmei.fhir.resource.terminology.CodeSystem)

    when:
    def cs = mapper.fromFhirCodeSystem(fhir)

    then: "the display-derivation rule is SNOMED-url only"
    displayOf(cs, "a") == null
  }

  def "a SNOMED designation use maps to the snomed-synonym designation type"() {
    given:
    def fhir = com.kodality.zmei.fhir.FhirMapper.fromJson('''
      {"resourceType":"CodeSystem","url":"http://example.org/x","status":"active","content":"complete","language":"en","concept":[
        {"code":"a","display":"A","designation":[
          {"use":{"system":"http://snomed.info/sct","code":"900000000000013009"},"language":"en","value":"A syn"}]}]}''',
        com.kodality.zmei.fhir.resource.terminology.CodeSystem)

    when:
    def cs = mapper.fromFhirCodeSystem(fhir)

    then:
    designationTypeOf(cs, "a", "A syn") == "snomed-synonym"
  }

  private static String displayOf(cs, String code) {
    def concept = cs.concepts.find { it.code == code }
    concept?.versions?.first()?.designations?.find { it.designationType == "display" }?.name
  }

  private static String designationTypeOf(cs, String code, String value) {
    def concept = cs.concepts.find { it.code == code }
    concept?.versions?.first()?.designations?.find { it.name == value }?.designationType
  }
}
