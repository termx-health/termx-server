package org.termx.terminology.fhir.valueset

import com.kodality.commons.model.LocalizedName
import org.termx.terminology.terminology.codesystem.CodeSystemService
import org.termx.terminology.terminology.codesystem.concept.ConceptService
import org.termx.terminology.terminology.mapset.MapSetService
import org.termx.terminology.terminology.relatedartifacts.ValueSetRelatedArtifactService
import org.termx.terminology.terminology.valueset.ValueSetService
import org.termx.ts.PublicationStatus
import org.termx.ts.codesystem.CodeSystemAssociation
import org.termx.ts.codesystem.Designation
import org.termx.ts.codesystem.EntityPropertyType
import org.termx.ts.codesystem.EntityPropertyValue
import org.termx.ts.valueset.ValueSet
import org.termx.ts.valueset.ValueSetSnapshot
import org.termx.ts.valueset.ValueSetVersion
import org.termx.ts.valueset.ValueSetVersionConcept
import org.termx.ts.valueset.ValueSetVersionConcept.ValueSetVersionConceptValue
import org.termx.ts.valueset.ValueSetVersionRuleSet
import org.termx.ts.valueset.ValueSetVersionRuleSet.ValueSetVersionRule
import spock.lang.Specification
import spock.lang.Timeout

import java.time.LocalDate
import java.util.concurrent.TimeUnit

class ValueSetFhirMapperExpansionSpec extends Specification {
  def conceptService = Mock(ConceptService)
  def codeSystemService = Mock(CodeSystemService)
  def valueSetService = Mock(ValueSetService)
  def mapSetService = Mock(MapSetService)
  def relatedArtifactService = Mock(ValueSetRelatedArtifactService)

  def mapper = new ValueSetFhirMapper(conceptService, codeSystemService, valueSetService, mapSetService, relatedArtifactService)

  static final String CS_URI = "https://www.dastacr.cz/dasta/nclp_data/ds_NCLP/all/nclppol.xml"

  def "expansion only surfaces declared properties, deduped, with coding version and declared expansion.property"() {
    given: "a value set version that declares exactly klic, klickomp and status"
    conceptService.load(_, _) >> Optional.empty()
    relatedArtifactService.findRelatedArtifacts(_) >> []
    def valueSet = new ValueSet().setId("vs").setUri("http://fhir.ee/ValueSet/vs").setName("vs").setTitle(new LocalizedName([en: "vs"]))
    def rule = new ValueSetVersionRule().setType("include").setCodeSystem("nclp-id-NCLPPOL").setProperties(["klic", "klickomp", "klicproc", "status"])
    def version = new ValueSetVersion().setVersion("2.92.01").setPreferredLanguage("cs")
        .setReleaseDate(LocalDate.parse("2025-05-15")).setStatus(PublicationStatus.active)
        .setRuleSet(new ValueSetVersionRuleSet().setRules([rule]))

    and: "a snapshot with one concept carrying a duplicate klic, a coding klickomp (version 0.1.1), and an UNDECLARED nazev"
    def concept = new ValueSetVersionConcept()
        .setConcept(new ValueSetVersionConceptValue().setConceptVersionId(4795762L).setCode("03861")
            .setCodeSystem("nclp-id-NCLPPOL").setCodeSystemUri(CS_URI).setCodeSystemVersions(["2.92.01"]))
        .setDisplay(new Designation().setName("Alfa-2-globulin").setLanguage("cs"))
        .setActive(true).setStatus("active")
        .setPropertyValues([
            new EntityPropertyValue().setEntityProperty("klic").setEntityPropertyType(EntityPropertyType.string).setValue("03861"),
            new EntityPropertyValue().setEntityProperty("klic").setEntityPropertyType(EntityPropertyType.string).setValue("03861"),
            new EntityPropertyValue().setEntityProperty("klickomp").setEntityPropertyType(EntityPropertyType.coding)
                .setValue([code: "A2GSQ", codeSystem: "NCLPKOMP", version: "0.1.1"]),
            new EntityPropertyValue().setEntityProperty("klicproc").setEntityPropertyType(EntityPropertyType.coding)
                .setValue([code: "ACCELP", codeSystem: "NCLPPROC", version: "0.1.1",
                           display: [[name: "Elektroforéza-acetylcelulóza", language: "cs", use: "display"]]]),
            new EntityPropertyValue().setEntityProperty("nazev").setEntityPropertyType(EntityPropertyType.string).setValue("Alfa-2-globulin (...)"),
        ])
    def snapshot = new ValueSetSnapshot().setValueSet("vs").setConceptsTotal(1).setExpansion([concept])

    when:
    def fhir = mapper.toFhir(valueSet, version, [], snapshot, null)
    def contains = fhir.expansion.contains
    def props = contains[0].property

    then: "the concept maps cleanly"
    contains.size() == 1
    contains[0].code == "03861"
    contains[0].system == CS_URI
    contains[0].version == "2.92.01"

    and: "only declared properties surface; the undeclared nazev is dropped and the duplicate klic is collapsed"
    props*.code == ["klic", "klickomp", "klicproc", "status"]

    and: "the coding-refresh version is preserved on the value coding"
    def klickomp = props.find { it.code == "klickomp" }
    klickomp.valueCoding.code == "A2GSQ"
    klickomp.valueCoding.system == "NCLPKOMP"
    klickomp.valueCoding.version == "0.1.1"

    and: "the coding-refresh localized display is preserved (resolved to the requested-less default language)"
    def klicproc = props.find { it.code == "klicproc" }
    klicproc.valueCoding.code == "ACCELP"
    klicproc.valueCoding.version == "0.1.1"
    klicproc.valueCoding.display == "Elektroforéza-acetylcelulóza"

    and: "the concept status property is surfaced"
    props.find { it.code == "status" }.valueCode == "active"

    and: "every emitted property is declared up front in expansion.property"
    fhir.expansion.property*.code == ["klic", "klickomp", "klicproc", "status"]
  }

  @Timeout(value = 10, unit = TimeUnit.SECONDS)
  def "nested expansion terminates on cyclic associations instead of recursing forever"() {
    given: "a root R with a child A that has a self-loop association (R <- A, A <- A)"
    conceptService.load(_, _) >> Optional.empty()
    relatedArtifactService.findRelatedArtifacts(_) >> []
    def valueSet = new ValueSet().setId("vs").setUri("http://fhir.ee/ValueSet/vs").setName("vs").setTitle(new LocalizedName([en: "vs"]))
    def version = new ValueSetVersion().setVersion("1.0.0").setPreferredLanguage("en")
        .setReleaseDate(LocalDate.parse("2026-06-17")).setStatus(PublicationStatus.active)
        .setRuleSet(new ValueSetVersionRuleSet().setRules([new ValueSetVersionRule().setType("include").setCodeSystem("cs")]))

    and:
    def root = concept("R", [])
    def child = concept("A", [assoc("R"), assoc("A")]) // points at its parent AND itself
    // nested (non-flat) rendering is the default when excludeNested is not set
    def snapshot = new ValueSetSnapshot().setValueSet("vs").setConceptsTotal(2).setExpansion([root, child])

    when:
    def fhir = mapper.toFhir(valueSet, version, [], snapshot, null)
    def contains = fhir.expansion.contains

    then: "it returns; R contains A once, and the self-loop under A is cut"
    contains*.code == ["R"]
    contains[0].contains*.code == ["A"]
    contains[0].contains[0].contains == []
  }

  def "fromFhir preserves designation.use code as designationType (compose + expansion)"() {
    given: "a FHIR ValueSet whose concept carries a typed designation, in both compose and expansion"
    def fhir = new com.kodality.zmei.fhir.resource.terminology.ValueSet().setId("vs").setUrl("http://fhir.ee/ValueSet/vs").setName("vs").setLanguage("en")
    def designation = new com.kodality.zmei.fhir.resource.terminology.ValueSet.ValueSetComposeIncludeConceptDesignation()
        .setValue("alias name").setLanguage("en").setUse(new com.kodality.zmei.fhir.datatypes.Coding("alias"))
    fhir.setCompose(new com.kodality.zmei.fhir.resource.terminology.ValueSet.ValueSetCompose().setInclude([
        new com.kodality.zmei.fhir.resource.terminology.ValueSet.ValueSetComposeInclude().setSystem("http://cs")
            .setConcept([new com.kodality.zmei.fhir.resource.terminology.ValueSet.ValueSetComposeIncludeConcept().setCode("A").setDisplay("A").setDesignation([designation])])]))
    fhir.setExpansion(new com.kodality.zmei.fhir.resource.terminology.ValueSet.ValueSetExpansion().setContains([
        new com.kodality.zmei.fhir.resource.terminology.ValueSet.ValueSetExpansionContains().setSystem("http://cs").setCode("A").setDisplay("A").setDesignation([designation])]))

    when:
    def imported = ValueSetFhirMapper.fromFhirValueSet(fhir)
    def composeDesignation = imported.versions.first().ruleSet.rules.first().concepts.first().additionalDesignations.first()
    def expansionDesignation = imported.versions.first().snapshot.expansion.first().additionalDesignations.first()

    then: "the designation type is recovered from use.code, not dropped"
    composeDesignation.name == "alias name"
    composeDesignation.designationType == "alias"
    expansionDesignation.designationType == "alias"
  }

  def "expansion derives used-codesystem params and does not echo the url selection parameter"() {
    given: "a value set version with two concepts: two share CS version 1.0.0, one comes from a second CS"
    conceptService.load(_, _) >> Optional.empty()
    relatedArtifactService.findRelatedArtifacts(_) >> []
    def valueSet = new ValueSet().setId("vs").setUri("http://fhir.ee/ValueSet/vs").setName("vs").setTitle(new LocalizedName([en: "vs"]))
    def version = new ValueSetVersion().setVersion("1.0.0").setPreferredLanguage("en")
        .setReleaseDate(LocalDate.parse("2026-06-17")).setStatus(PublicationStatus.active)
        .setRuleSet(new ValueSetVersionRuleSet().setRules([new ValueSetVersionRule().setType("include").setCodeSystem("cs")]))

    and:
    def a = new ValueSetVersionConcept().setConcept(new ValueSetVersionConceptValue().setCode("A")
        .setCodeSystem("cs1").setCodeSystemUri("http://cs1").setCodeSystemVersions(["1.0.0"]))
        .setDisplay(new Designation().setName("A").setLanguage("en")).setActive(true).setStatus("active")
    def b = new ValueSetVersionConcept().setConcept(new ValueSetVersionConceptValue().setCode("B")
        .setCodeSystem("cs1").setCodeSystemUri("http://cs1").setCodeSystemVersions(["1.0.0"]))
        .setDisplay(new Designation().setName("B").setLanguage("en")).setActive(true).setStatus("active")
    def c = new ValueSetVersionConcept().setConcept(new ValueSetVersionConceptValue().setCode("C")
        .setCodeSystem("cs2").setCodeSystemUri("http://cs2").setCodeSystemVersions(["2.1.0"]))
        .setDisplay(new Designation().setName("C").setLanguage("en")).setActive(true).setStatus("active")
    def snapshot = new ValueSetSnapshot().setValueSet("vs").setConceptsTotal(3).setExpansion([a, b, c])

    and: "a request carrying the url selection param plus a real excludeNested control param"
    def param = new com.kodality.zmei.fhir.resource.other.Parameters().setParameter([
        new com.kodality.zmei.fhir.resource.other.Parameters.ParametersParameter().setName("url").setValueUri("http://fhir.ee/ValueSet/vs"),
        new com.kodality.zmei.fhir.resource.other.Parameters.ParametersParameter().setName("excludeNested").setValueBoolean(true),
    ])

    when:
    def fhir = mapper.toFhir(valueSet, version, [], snapshot, param)
    def params = fhir.expansion.parameter

    then: "the url selection identifier is NOT echoed; excludeNested is"
    params.find { it.name == "url" } == null
    params.find { it.name == "excludeNested" }.valueBoolean == true

    and: "one used-codesystem per distinct system|version, deduped across the two cs1 concepts, order preserved"
    params.findAll { it.name == "used-codesystem" }*.valueUri == ["http://cs1|1.0.0", "http://cs2|2.1.0"]
  }

  private static ValueSetVersionConcept concept(String code, List<CodeSystemAssociation> associations) {
    new ValueSetVersionConcept()
        .setConcept(new ValueSetVersionConceptValue().setCode(code).setCodeSystem("cs").setCodeSystemUri("http://cs"))
        .setDisplay(new Designation().setName(code).setLanguage("en"))
        .setActive(true).setStatus("active")
        .setAssociations(associations)
  }

  private static CodeSystemAssociation assoc(String targetCode) {
    new CodeSystemAssociation().setTargetCode(targetCode).setAssociationType("is-a")
  }
}
