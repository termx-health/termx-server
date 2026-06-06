package org.termx.terminology.fhir.valueset

import com.kodality.commons.model.LocalizedName
import com.kodality.zmei.fhir.Extension
import org.termx.terminology.terminology.codesystem.CodeSystemService
import org.termx.terminology.terminology.codesystem.concept.ConceptService
import org.termx.terminology.terminology.mapset.MapSetService
import org.termx.terminology.terminology.relatedartifacts.ValueSetRelatedArtifactService
import org.termx.terminology.terminology.valueset.ValueSetService
import org.termx.ts.PublicationStatus
import org.termx.ts.valueset.ValueSet
import org.termx.ts.valueset.ValueSetVersion
import org.termx.ts.valueset.ValueSetVersionRuleSet
import spock.lang.Specification

import java.time.LocalDate

class ValueSetFhirMapperLanguageSpec extends Specification {
  def conceptService = Mock(ConceptService)
  def codeSystemService = Mock(CodeSystemService)
  def valueSetService = Mock(ValueSetService)
  def mapSetService = Mock(MapSetService)
  def relatedArtifactService = Mock(ValueSetRelatedArtifactService)

  def mapper = new ValueSetFhirMapper(conceptService, codeSystemService, valueSetService, mapSetService, relatedArtifactService)

  def "toFhir exports valueset-language extensions from supportedLanguages"() {
    given:
    conceptService.load(_, _) >> Optional.empty()
    relatedArtifactService.findRelatedArtifacts(_) >> []
    def valueSet = new ValueSet().setId("vs").setUri("http://fhir.ee/ValueSet/vs").setName("vs").setTitle(new LocalizedName([en: "vs"]))
    def version = new ValueSetVersion().setVersion("1.0.0").setPreferredLanguage("en")
        .setReleaseDate(LocalDate.parse("2026-03-18")).setStatus(PublicationStatus.draft)
        .setRuleSet(new ValueSetVersionRuleSet().setRules([]))
        .setSupportedLanguages(["et", "en"])

    when:
    def fhir = mapper.toFhir(valueSet, version, [])
    def languages = fhir.extension.findAll { it.url == "https://termx.org/fhir/StructureDefinition/valueset-language" }.collect { it.valueCode }

    then:
    languages == ["et", "en"]
  }

  def "fromFhir imports valueset-language extensions into the version supportedLanguages"() {
    given:
    def fhir = new com.kodality.zmei.fhir.resource.terminology.ValueSet()
        .setId("vs").setUrl("http://fhir.ee/ValueSet/vs").setName("vs").setLanguage("en")
        .addExtension(new Extension("https://termx.org/fhir/StructureDefinition/valueset-language").setValueCode("et"))
        .addExtension(new Extension("https://termx.org/fhir/StructureDefinition/valueset-language").setValueCode("ru"))

    when:
    def imported = ValueSetFhirMapper.fromFhirValueSet(fhir)
    def languages = imported.versions.first().supportedLanguages

    then:
    languages.contains("et")
    languages.contains("ru")
  }
}
