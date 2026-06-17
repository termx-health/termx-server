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

  def "(#47) multiple supplement-bound rules round-trip when every include is a supplement (paired by order)"() {
    given:
    conceptService.load(_, _) >> Optional.empty()
    relatedArtifactService.findRelatedArtifacts(_) >> []
    def ruleA = supplementRule("http://example.org/CodeSystem/base-a-lt", "http://example.org/CodeSystem/base-a", "2026", "1.0")
    def ruleB = supplementRule("http://example.org/CodeSystem/base-b-lt", "http://example.org/CodeSystem/base-b", "2025", "2.0")
    def valueSet = new ValueSet().setId("vs").setUri("http://fhir.ee/ValueSet/vs").setName("vs").setTitle(new LocalizedName([en: "vs"]))
    def version = new ValueSetVersion().setVersion("1.0.0").setStatus(PublicationStatus.draft)
        .setReleaseDate(LocalDate.parse("2026-03-18"))
        .setRuleSet(new ValueSetVersionRuleSet().setRules([ruleA, ruleB]))

    when:
    def fhir = mapper.toFhir(valueSet, version, [])
    def rules = ValueSetFhirMapper.fromFhirValueSet(fhir).versions.first().ruleSet.rules

    then: "two valueset-supplement extensions are emitted"
    fhir.extension.findAll { it.url == SUPPLEMENT_EXT }.size() == 2

    and: "both rules' supplement bindings are restored, correctly paired to their base by order"
    def a = rules.find { it.codeSystemBaseUri == "http://example.org/CodeSystem/base-a" }
    def b = rules.find { it.codeSystemBaseUri == "http://example.org/CodeSystem/base-b" }
    a.codeSystemUri == "http://example.org/CodeSystem/base-a-lt"
    a.codeSystemVersion.version == "2026"
    b.codeSystemUri == "http://example.org/CodeSystem/base-b-lt"
    b.codeSystemVersion.version == "2025"
  }

  private static ValueSetVersionRule supplementRule(String supplementUri, String baseUri, String supplementVersion, String baseVersion) {
    return new ValueSetVersionRule().setType("include")
        .setCodeSystemUri(supplementUri).setCodeSystemBaseUri(baseUri)
        .setCodeSystemVersion(new CodeSystemVersionReference().setVersion(supplementVersion)
            .setBaseCodeSystemVersion(new CodeSystemVersionReference().setVersion(baseVersion)))
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
