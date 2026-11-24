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
import org.termx.ts.codesystem.CodeSystemAssociation
import org.termx.ts.codesystem.CodeSystemEntityVersion
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

  def "a designation imported without a use round-trips to FHIR without throwing"() {
    given: "FHIR-valid content whose designation omits the optional `use`"
    def fhir = com.kodality.zmei.fhir.FhirMapper.fromJson('''
      {"resourceType":"CodeSystem","url":"http://example.org/x","status":"active","content":"complete","language":"en","concept":[
        {"code":"a","display":"A","designation":[
          {"language":"en","value":"A alt"},
          {"use":{"system":"http://snomed.info/sct","code":"900000000000013009"},"language":"en","value":"A syn"}]}]}''',
        com.kodality.zmei.fhir.resource.terminology.CodeSystem)
    conceptService.load(_, _) >> Optional.empty()
    codeSystemService.query(_) >> new QueryResult<CodeSystem>([])

    and: "it is stored under the internal `alternate` marker, which exports with no use"
    def cs = mapper.fromFhirCodeSystem(fhir)
    designationTypeOf(cs, "a", "A alt") == "alternate"

    when: "the stored code system is read back out as FHIR"
    def version = cs.versions.first().setEntities(cs.concepts.collect { it.versions.first().setCode(it.code) })
    def exported = mapper.toFhir(cs, version, null)

    then: "the use-less designation sorts and exports with no use, rather than NPEing"
    noExceptionThrown()
    def designations = exported.concept.find { it.code == "a" }.designation
    designations*.value == ["A alt", "A syn"]
    designations.find { it.value == "A alt" }.use == null
    designations.find { it.value == "A syn" }.use.code == "900000000000013009"
  }

  // ---- hierarchy shapes (CSD-1: "all the codes SHALL be unique") ----

  /** Every concept in the resource, nested children included — the shape-independent view. */
  private static List allOf(List concepts) {
    (concepts ?: []).collectMany { [it] + allOf(it.concept) }
  }

  private static conceptOf(resource, String code) {
    allOf(resource.concept).find { it.code == code }
  }

  /** The shape the resource announces it was written in. */
  private static String shapeOf(resource) {
    resource.getExtension("https://termx.org/fhir/StructureDefinition/codesystem-hierarchy-shape")
        .map { it.valueCode }.orElse(null)
  }


  /** A concept with two parents — the shape that used to be emitted once per parent. */
  private static hierarchicalCodeSystem(String hierarchyMeaning, List<EntityProperty> properties) {
    def cs = new CodeSystem().setId("panels").setUri("http://example.org/panels").setName("panels")
        .setTitle(new LocalizedName([en: "panels"])).setContent("complete")
        .setHierarchyMeaning(hierarchyMeaning).setProperties(properties)
    def panelA = new CodeSystemEntityVersion().setId(1L).setCode("panel-a")
    def panelB = new CodeSystemEntityVersion().setId(2L).setCode("panel-b")
    def analyte = new CodeSystemEntityVersion().setId(3L).setCode("analyte").setAssociations([
        new CodeSystemAssociation().setAssociationType(hierarchyMeaning).setSourceId(3L).setTargetId(1L).setTargetCode("panel-a"),
        new CodeSystemAssociation().setAssociationType(hierarchyMeaning).setSourceId(3L).setTargetId(2L).setTargetCode("panel-b")])
    def version = new CodeSystemVersion().setVersion("1.0.0").setPreferredLanguage("en")
        .setReleaseDate(LocalDate.parse("2026-03-18")).setStatus(PublicationStatus.draft)
        .setConceptsTotal(3).setEntities([panelA, panelB, analyte])
    [cs, version]
  }

  def "toFhir emits each code exactly once even when a concept has several parents"() {
    given: "an analyte that is part of two panels"
    conceptService.load(_, _) >> Optional.empty()
    codeSystemService.query(_) >> new QueryResult<CodeSystem>([])
    def (cs, version) = hierarchicalCodeSystem("part-of", [new EntityProperty().setName("partOf").setType(EntityPropertyType.code)])

    when: "no shape is set, so the server derives one"
    def exported = mapper.toFhir(cs, version, null)

    then: "a DAG derives flat: nesting it is exactly what breaks CSD-1, so the default will not"
    shapeOf(exported) == "flat"

    and: "each code exactly once, and count agrees with it"
    exported.concept*.code == ["analyte", "panel-a", "panel-b"]
    exported.concept.every { it.concept == null }
    exported.count == exported.concept.size()

    and: "both parents survive as properties — flattening loses nothing"
    conceptOf(exported, "analyte").property.findAll { it.code == "partOf" }*.valueCode.toSorted() == ["panel-a", "panel-b"]
  }

  def "a tree derives nested, so an ordinary code system gets its hierarchy back untouched"() {
    given: "the same code system with the second panel membership removed, making it a true tree"
    conceptService.load(_, _) >> Optional.empty()
    codeSystemService.query(_) >> new QueryResult<CodeSystem>([])
    def (cs, version) = hierarchicalCodeSystem("part-of", [new EntityProperty().setName("partOf").setType(EntityPropertyType.code)])
    version.entities.find { it.code == "analyte" }.associations.removeAll { it.targetCode == "panel-b" }

    when:
    def exported = mapper.toFhir(cs, version, null)

    then: "nesting a tree repeats no code, so the old output is also the valid one"
    shapeOf(exported) == "nested"
    exported.concept*.code == ["panel-a", "panel-b"]
    conceptOf(exported, "panel-a").concept*.code == ["analyte"]
    allOf(exported.concept)*.code.toSorted() == ["analyte", "panel-a", "panel-b"]
  }

  def "a version that asks for #shape gets it, whatever the data looks like"() {
    given:
    conceptService.load(_, _) >> Optional.empty()
    codeSystemService.query(_) >> new QueryResult<CodeSystem>([])
    def (cs, version) = hierarchicalCodeSystem("part-of", [new EntityProperty().setName("partOf").setType(EntityPropertyType.code)])
    version.setHierarchyShape(shape)

    when:
    def exported = mapper.toFhir(cs, version, null)

    then: "the chosen shape is honoured and announced"
    shapeOf(exported) == shape
    exported.concept*.code == roots
    allOf(exported.concept)*.code.toSorted() == nodes

    where: "`nested` on a DAG repeats the analyte under both panels — that IS the old output"
    shape    | roots                             | nodes
    "flat"   | ["analyte", "panel-a", "panel-b"] | ["analyte", "panel-a", "panel-b"]
    "nested" | ["panel-a", "panel-b"]            | ["analyte", "analyte", "panel-a", "panel-b"]
  }

  def "an unknown shape is no setting at all, not an export failure"() {
    given:
    conceptService.load(_, _) >> Optional.empty()
    codeSystemService.query(_) >> new QueryResult<CodeSystem>([])
    def (cs, version) = hierarchicalCodeSystem("part-of", [new EntityProperty().setName("partOf").setType(EntityPropertyType.code)])
    version.setHierarchyShape("sideways")

    when:
    def exported = mapper.toFhir(cs, version, null)

    then: "it falls back to the derived shape rather than being treated as one of the two"
    shapeOf(exported) == "flat"
  }

  def "a code system with no hierarchy exports flat, since there is nothing to nest"() {
    given:
    conceptService.load(_, _) >> Optional.empty()
    codeSystemService.query(_) >> new QueryResult<CodeSystem>([])
    def cs = new CodeSystem().setId("plain").setUri("http://example.org/plain").setName("plain")
        .setTitle(new LocalizedName([en: "plain"])).setContent("complete")
    def version = new CodeSystemVersion().setVersion("1.0.0").setPreferredLanguage("en")
        .setReleaseDate(LocalDate.parse("2026-03-18")).setStatus(PublicationStatus.draft).setConceptsTotal(2)
        .setEntities([new CodeSystemEntityVersion().setId(1L).setCode("a"), new CodeSystemEntityVersion().setId(2L).setCode("b")])

    when:
    def exported = mapper.toFhir(cs, version, null)

    then:
    shapeOf(exported) == "flat"
    exported.concept*.code == ["a", "b"]
  }

  def "a cycle below a root terminates instead of recursing until the stack goes"() {
    given: "root > a > b, and b is also a parent of a — the loop the old recursion fell into"
    conceptService.load(_, _) >> Optional.empty()
    codeSystemService.query(_) >> new QueryResult<CodeSystem>([])
    def cs = new CodeSystem().setId("loop").setUri("http://example.org/loop").setName("loop")
        .setTitle(new LocalizedName([en: "loop"])).setContent("complete").setHierarchyMeaning("is-a")
    def isa = { Long from, Long to, String toCode ->
      new CodeSystemAssociation().setAssociationType("is-a").setSourceId(from).setTargetId(to).setTargetCode(toCode)
    }
    def root = new CodeSystemEntityVersion().setId(1L).setCode("root")
    def a = new CodeSystemEntityVersion().setId(2L).setCode("a").setAssociations([isa(2L, 1L, "root"), isa(2L, 3L, "b")])
    def b = new CodeSystemEntityVersion().setId(3L).setCode("b").setAssociations([isa(3L, 2L, "a")])
    def version = new CodeSystemVersion().setVersion("1.0.0").setPreferredLanguage("en")
        .setReleaseDate(LocalDate.parse("2026-03-18")).setStatus(PublicationStatus.draft).setConceptsTotal(3)
        .setEntities([root, a, b]).setHierarchyShape("nested")

    when:
    def exported = mapper.toFhir(cs, version, null)

    then: "the walk stops when it would revisit a concept already on the path"
    noExceptionThrown()
    exported.concept*.code == ["root"]
    conceptOf(exported, "a").concept*.code == ["b"]
    conceptOf(exported, "b").concept == null
  }

  def "a code system that is nothing but a cycle has no roots, exactly as before"() {
    given: "a and b are each other's only parent, so neither is a root"
    conceptService.load(_, _) >> Optional.empty()
    codeSystemService.query(_) >> new QueryResult<CodeSystem>([])
    def cs = new CodeSystem().setId("loop2").setUri("http://example.org/loop2").setName("loop2")
        .setTitle(new LocalizedName([en: "loop2"])).setContent("complete").setHierarchyMeaning("is-a")
    def a = new CodeSystemEntityVersion().setId(1L).setCode("a").setAssociations(
        [new CodeSystemAssociation().setAssociationType("is-a").setSourceId(1L).setTargetId(2L).setTargetCode("b")])
    def b = new CodeSystemEntityVersion().setId(2L).setCode("b").setAssociations(
        [new CodeSystemAssociation().setAssociationType("is-a").setSourceId(2L).setTargetId(1L).setTargetCode("a")])
    def version = new CodeSystemVersion().setVersion("1.0.0").setPreferredLanguage("en")
        .setReleaseDate(LocalDate.parse("2026-03-18")).setStatus(PublicationStatus.draft).setConceptsTotal(2)
        .setEntities([a, b]).setHierarchyShape("nested")

    when:
    def exported = mapper.toFhir(cs, version, null)

    then: "the nested shape is root-down, so it emits nothing — this is the restored behaviour, not new"
    exported.concept == []

    and: "`flat` is the shape that can carry data like this, and it does"
    mapper.toFhir(cs, version.setHierarchyShape("flat"), null).concept*.code == ["a", "b"]
  }

  // ---- fixture pair: the same polyhierarchy, serialised wrongly and rightly ----
  //
  // Two panels, three analytes, one analyte (Glucose) in BOTH panels. The nested fixture is what
  // termx used to emit: Glucose appears once per parent, so the resource carries 6 concept nodes for
  // 5 codes and contradicts its own `count`. The flat fixture is the fix, and is also how SNOMED CT
  // and LOINC publish. Both are readable on their own — they double as the explanation of the defect.

  private static loadFixture(String name) {
    def json = new String(CodeSystemFhirMapperSpec.classLoader.getResourceAsStream("fhir/codesystem/${name}").readAllBytes(), "UTF-8")
    com.kodality.zmei.fhir.FhirMapper.fromJson(json, com.kodality.zmei.fhir.resource.terminology.CodeSystem)
  }

  /**
   * Stands in for persistence: dedupe by code the way CodeSystemImportService.prepareConcepts does
   * (a nested input yields one Concept per nesting occurrence), then assign the entity ids the export
   * keys its hierarchy on. The surviving occurrence keeps every parent only because getParentMap now
   * walks nested concepts — reading just the top level would leave it with its nesting parent alone.
   */
  private static versionOf(cs) {
    def byCode = new LinkedHashMap<String, Object>()
    cs.concepts.each { byCode.putIfAbsent(it.code, it) }
    def entities = byCode.values().collect { it.versions.first().setCode(it.code) }
    entities.eachWithIndex { e, i -> e.setId((i + 1) as Long) }
    def idByCode = entities.collectEntries { [(it.code): it.id] }
    entities.each { e ->
      e.associations?.each { it.setSourceId(e.id).setTargetId(idByCode[it.targetCode] as Long) }
    }
    cs.versions.first().setEntities(entities).setConceptsTotal(entities.size())
  }

  private static countNodes(List concepts) {
    concepts.sum { 1 + (it.concept ? countNodes(it.concept) : 0) } ?: 0
  }

  def "the nested fixture documents the defect: a code emitted once per parent"() {
    when:
    def fhir = loadFixture("polyhierarchy-INVALID-nested.json")

    then: "6 nodes for 5 codes — Glucose twice — and `count` disagrees with the tree shipped beside it"
    countNodes(fhir.concept) == 6
    fhir.count == 5
    def codes = []
    fhir.concept.each { p -> codes << p.code; p.concept?.each { codes << it.code } }
    codes.count { it == "2345-7" } == 2
  }

  def "the nested shape reproduces the pre-flattening export, duplicate codes and all"() {
    given: "the fixture IS what termx used to emit, so re-exporting it is the comparison"
    conceptService.load(_, _) >> Optional.empty()
    codeSystemService.query(_) >> new QueryResult<CodeSystem>([])
    def original = loadFixture("polyhierarchy-INVALID-nested.json")
    def cs = mapper.fromFhirCodeSystem(original)

    when: "the version asks for the old shape explicitly"
    def exported = mapper.toFhir(cs, versionOf(cs).setHierarchyShape("nested"), null)

    then: "same roots, same children under each, same order — Glucose under both panels again"
    exported.concept*.code == original.concept*.code
    exported.concept.collect { it.concept*.code } == original.concept.collect { it.concept*.code }

    and: "which means the same 6 nodes for 5 codes, exactly as before"
    countNodes(exported.concept) == countNodes(original.concept)
    countNodes(exported.concept) == 6
  }

  def "re-exporting the nested fixture with no setting repairs the duplication"() {
    given:
    conceptService.load(_, _) >> Optional.empty()
    codeSystemService.query(_) >> new QueryResult<CodeSystem>([])
    def cs = mapper.fromFhirCodeSystem(loadFixture("polyhierarchy-INVALID-nested.json"))
    def version = versionOf(cs)

    expect: "nesting ALONE records no setting: the shape nobody chose must not be frozen in"
    version.hierarchyShape == null

    when:
    def exported = mapper.toFhir(cs, version, null)

    then: "so the derived shape applies, and this is a DAG, so it is flat"
    shapeOf(exported) == "flat"

    and: "5 codes, 5 nodes, count in agreement"
    def all = allOf(exported.concept)
    all*.code.sort() == ["2093-3", "2160-0", "2345-7", "PANEL-LIPID", "PANEL-METAB"]
    exported.count == all.size()

    and: "both panel memberships survive — what the nesting could only say by repeating itself"
    conceptOf(exported, "2345-7").property.findAll { it.code == "partOf" }*.valueCode.sort() ==
        ["PANEL-LIPID", "PANEL-METAB"]

    and: "the property carrying them is declared, so the resource stays self-describing"
    exported.property.find { it.code == "partOf" } != null
  }

  def "the flat fixture round-trips unchanged"() {
    given:
    conceptService.load(_, _) >> Optional.empty()
    codeSystemService.query(_) >> new QueryResult<CodeSystem>([])
    def original = loadFixture("polyhierarchy-VALID-flat.json")
    def cs = mapper.fromFhirCodeSystem(original)

    when:
    def exported = mapper.toFhir(cs, versionOf(cs), null)

    then: "a flat resource is served back flat — this is what recording the shape buys"
    cs.versions.first().hierarchyShape == "flat"
    original.concept*.code.sort() == exported.concept*.code.sort()
    exported.concept.every { it.concept == null }
    exported.count == exported.concept.size()

    and: "the partOf edges are preserved exactly, Glucose included"
    def parentsOf = { resource, code ->
      (resource.concept.find { it.code == code }.property ?: []).findAll { it.code == "partOf" }*.valueCode.sort()
    }
    parentsOf(exported, "2345-7") == parentsOf(original, "2345-7")
    parentsOf(exported, "2093-3") == ["PANEL-LIPID"]
    parentsOf(exported, "PANEL-LIPID") == []
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
