package org.termx.core.fhir

import com.kodality.zmei.fhir.Element
import com.kodality.zmei.fhir.Extension
import spock.lang.Specification

/**
 * Migrated from tehik fork KL-118: fromFhirName() must retain FHIR translation
 * extensions even when the primary name is null, instead of discarding them.
 */
class BaseFhirMapperNameSpec extends Specification {

  private static Element elementWithTranslation(String lang, String content) {
    def translation = new Extension("http://hl7.org/fhir/StructureDefinition/translation")
    translation.addExtension(new Extension("lang").setValueCode(lang))
    translation.addExtension(new Extension("content").setValueString(content))
    def element = new Element()
    element.addExtension(translation)
    return element
  }

  def "keeps translations when the primary name is null"() {
    when:
    def result = BaseFhirMapper.fromFhirName(null, "en", elementWithTranslation("et", "Eesti nimi"))

    then:
    result != null
    result.get("et") == "Eesti nimi"
  }

  def "combines the primary name with translations"() {
    when:
    def result = BaseFhirMapper.fromFhirName("English name", "en", elementWithTranslation("et", "Eesti nimi"))

    then:
    result.get("en") == "English name"
    result.get("et") == "Eesti nimi"
  }
}
