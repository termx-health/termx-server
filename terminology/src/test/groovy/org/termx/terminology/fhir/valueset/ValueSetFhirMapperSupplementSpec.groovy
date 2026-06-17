package org.termx.terminology.fhir.valueset

import com.kodality.commons.model.LocalizedName
import org.termx.terminology.terminology.codesystem.CodeSystemService
import org.termx.terminology.terminology.codesystem.concept.ConceptService
import org.termx.terminology.terminology.mapset.MapSetService
import org.termx.terminology.terminology.relatedartifacts.ValueSetRelatedArtifactService
import org.termx.terminology.terminology.valueset.ValueSetService
import org.termx.ts.PublicationStatus
import org.termx.ts.codesystem.CodeSystemVersionReference
import org.termx.ts.valueset.ValueSet
import org.termx.ts.valueset.ValueSetVersion
import org.termx.ts.valueset.ValueSetVersionRuleSet
import org.termx.ts.valueset.ValueSetVersionRuleSet.ValueSetVersionRule
import spock.lang.Specification

import java.time.LocalDate

class ValueSetFhirMapperSupplementSpec extends Specification {
  def conceptService = Mock(ConceptService)
  def codeSystemService = Mock(CodeSystemService)
  def valueSetService = Mock(ValueSetService)
  def mapSetService = Mock(MapSetService)
  def relatedArtifactService = Mock(ValueSetRelatedArtifactService)

  def mapper = new ValueSetFhirMapper(conceptService, codeSystemService, valueSetService, mapSetService, relatedArtifactService)

  static final String SUPPLEMENT_EXT = "http://hl7.org/fhir/StructureDefinition/valueset-supplement"

  def "(#47) a supplement-bound rule round-trips through toFhir -> fromFhir"() {
    given: "a value set whose include rule binds a supplement, with the base recorded separately"
    conceptService.load(_, _) >> Optional.empty()
    relatedArtifactService.findRelatedArtifacts(_) >> []
    def rule = new ValueSetVersionRule()
        .setType("include")
        .setCodeSystemUri("http://example.org/CodeSystem/base-supplement-lt")
        .setCodeSystemBaseUri("http://example.org/CodeSystem/base")
        .setCodeSystemVersion(new CodeSystemVersionReference().setVersion("2026")
            .setBaseCodeSystemVersion(new CodeSystemVersionReference().setVersion("1.0")))
    def valueSet = new ValueSet().setId("vs").setUri("http://fhir.ee/ValueSet/vs").setName("vs").setTitle(new LocalizedName([en: "vs"]))
    def version = new ValueSetVersion().setVersion("1.0.0").setStatus(PublicationStatus.draft)
        .setReleaseDate(LocalDate.parse("2026-03-18"))
        .setRuleSet(new ValueSetVersionRuleSet().setRules([rule]))

    when:
    def fhir = mapper.toFhir(valueSet, version, [])
    def importedRule = ValueSetFhirMapper.fromFhirValueSet(fhir).versions.first().ruleSet.rules.first()

    then: "export puts the base on include.system and the supplement in the valueset-supplement extension"
    fhir.compose.include.first().system == "http://example.org/CodeSystem/base"
    fhir.extension.any { it.url == SUPPLEMENT_EXT && it.valueCanonical == "http://example.org/CodeSystem/base-supplement-lt|2026" }

    and: "import restores the supplement binding instead of discarding it (issue #47)"
    importedRule.codeSystemUri == "http://example.org/CodeSystem/base-supplement-lt"
    importedRule.codeSystemBaseUri == "http://example.org/CodeSystem/base"
    importedRule.codeSystemVersion.version == "2026"
    importedRule.codeSystemVersion.baseCodeSystemVersion.version == "1.0"
  }

  def "(#47) a plain (non-supplement) include is unaffected"() {
    given:
    conceptService.load(_, _) >> Optional.empty()
    relatedArtifactService.findRelatedArtifacts(_) >> []
    def rule = new ValueSetVersionRule().setType("include")
        .setCodeSystemUri("http://example.org/CodeSystem/plain")
        .setCodeSystemVersion(new CodeSystemVersionReference().setVersion("1.0"))
    def valueSet = new ValueSet().setId("vs").setUri("http://fhir.ee/ValueSet/vs").setName("vs").setTitle(new LocalizedName([en: "vs"]))
    def version = new ValueSetVersion().setVersion("1.0.0").setStatus(PublicationStatus.draft)
        .setReleaseDate(LocalDate.parse("2026-03-18"))
        .setRuleSet(new ValueSetVersionRuleSet().setRules([rule]))

    when:
    def fhir = mapper.toFhir(valueSet, version, [])
    def importedRule = ValueSetFhirMapper.fromFhirValueSet(fhir).versions.first().ruleSet.rules.first()

    then: "no supplement extension, and the rule still binds the original code system"
    !fhir.extension.any { it.url == SUPPLEMENT_EXT }
    importedRule.codeSystemUri == "http://example.org/CodeSystem/plain"
    importedRule.codeSystemBaseUri == null
  }
}
