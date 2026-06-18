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

  def "toFhir exports supported-language extensions from supportedLanguages"() {
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
    def languages = fhir.extension.findAll { it.url == "https://termx.org/fhir/StructureDefinition/supported-language" }.collect { it.valueCode }

    then:
    languages == ["et", "en"]
  }

  def "fromFhir imports supported-language extensions into the version supportedLanguages"() {
    given:
    def fhir = new com.kodality.zmei.fhir.resource.terminology.ValueSet()
        .setId("vs").setUrl("http://fhir.ee/ValueSet/vs").setName("vs").setLanguage("en")
        .addExtension(new Extension("https://termx.org/fhir/StructureDefinition/supported-language").setValueCode("et"))
        .addExtension(new Extension("https://termx.org/fhir/StructureDefinition/supported-language").setValueCode("ru"))

    when:
    def imported = ValueSetFhirMapper.fromFhirValueSet(fhir)
    def languages = imported.versions.first().supportedLanguages

    then:
    languages.contains("et")
    languages.contains("ru")
  }

  def "fromFhir defaults an absent title and name to the id (derived from the url's last segment)"() {
    given: "a ValueSet with neither title nor name nor id — only a url"
    def fhir = new com.kodality.zmei.fhir.resource.terminology.ValueSet()
        .setUrl("http://hl7.org/fhir/test/ValueSet/simple-all")
        .setStatus("active")

    when:
    def vs = ValueSetFhirMapper.fromFhirValueSet(fhir)

    then: "title is non-null (TermX stores it NOT NULL) and falls back through name -> id -> url last segment"
    vs.id == "simple-all"
    vs.name == "simple-all"
    vs.title != null
    vs.title.values().contains("simple-all")
    and: "the version's value_set FK uses the derived id (else the version insert hits a not-null violation)"
    vs.versions.first().valueSet == "simple-all"
  }
}
