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
