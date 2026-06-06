package org.termx.terminology.fhir.conceptmap

import com.kodality.commons.model.LocalizedName
import com.kodality.zmei.fhir.Extension
import com.kodality.zmei.fhir.resource.terminology.ConceptMap
import org.termx.terminology.terminology.codesystem.CodeSystemService
import org.termx.terminology.terminology.codesystem.concept.ConceptService
import org.termx.terminology.terminology.codesystem.version.CodeSystemVersionService
import org.termx.terminology.terminology.mapset.MapSetService
import org.termx.terminology.terminology.relatedartifacts.MapSetRelatedArtifactService
import org.termx.terminology.terminology.valueset.ValueSetService
import org.termx.terminology.terminology.valueset.expansion.ValueSetVersionConceptService
import org.termx.terminology.terminology.valueset.version.ValueSetVersionService
import org.termx.ts.PublicationStatus
import org.termx.ts.mapset.MapSet
import org.termx.ts.mapset.MapSetVersion
import spock.lang.Specification

import java.time.LocalDate

class ConceptMapFhirMapperLanguageSpec extends Specification {
  def conceptService = Mock(ConceptService)
  def codeSystemService = Mock(CodeSystemService)
  def valueSetService = Mock(ValueSetService)
  def mapSetService = Mock(MapSetService)
  def codeSystemVersionService = Mock(CodeSystemVersionService)
  def valueSetVersionService = Mock(ValueSetVersionService)
  def valueSetVersionConceptService = Mock(ValueSetVersionConceptService)
  def relatedArtifactService = Mock(MapSetRelatedArtifactService)

  def mapper = new ConceptMapFhirMapper(conceptService, codeSystemService, valueSetService, mapSetService,
      codeSystemVersionService, valueSetVersionService, valueSetVersionConceptService, relatedArtifactService)

  def "toFhir exports supported-language extensions from supportedLanguages"() {
    given:
    conceptService.load(_, _) >> Optional.empty()
    relatedArtifactService.findRelatedArtifacts(_) >> []
    def mapSet = new MapSet().setId("ms").setUri("http://fhir.ee/ConceptMap/ms").setName("ms").setTitle(new LocalizedName([en: "ms"]))
    def version = new MapSetVersion().setVersion("1.0.0").setPreferredLanguage("en")
        .setReleaseDate(LocalDate.parse("2026-03-18")).setStatus(PublicationStatus.draft)
        .setScope(new MapSetVersion.MapSetVersionScope())
        .setSupportedLanguages(["et", "en"])

    when:
    def fhir = mapper.toFhir(mapSet, version, [])
    def languages = fhir.extension.findAll { it.url == "https://termx.org/fhir/StructureDefinition/supported-language" }.collect { it.valueCode }

    then:
    languages == ["et", "en"]
  }

  def "fromFhir imports supported-language extensions into the version supportedLanguages"() {
    given:
    def fhir = new ConceptMap()
        .setId("ms").setUrl("http://fhir.ee/ConceptMap/ms").setName("ms").setLanguage("en")
        .addExtension(new Extension("https://termx.org/fhir/StructureDefinition/supported-language").setValueCode("et"))
        .addExtension(new Extension("https://termx.org/fhir/StructureDefinition/supported-language").setValueCode("ru"))

    when:
    def imported = mapper.fromFhir(fhir)
    def languages = imported.versions.first().supportedLanguages

    then:
    languages.contains("et")
    languages.contains("ru")
  }
}
