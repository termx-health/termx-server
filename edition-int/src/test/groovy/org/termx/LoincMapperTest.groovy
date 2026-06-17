package org.termx

import org.termx.editionint.loinc.utils.LoincConcept
import org.termx.editionint.loinc.utils.LoincConcept.LoincConceptProperty
import org.termx.editionint.loinc.utils.LoincImportRequest
import org.termx.editionint.loinc.utils.LoincMapper
import org.termx.ts.codesystem.Concept
import org.termx.ts.codesystem.EntityPropertyType
import spock.lang.Specification

class LoincMapperTest extends Specification {

  def "(#48) Coding-typed property definitions carry the referenced external code system binding"() {
    given: "two concepts whose 'answer-list' property values point into the answer-list code system"
    def concepts = [
        loincConcept("1234-5", "LL1", "answer-list"),
        loincConcept("6789-0", "LL2", "answer-list"),
    ]

    when:
    def request = LoincMapper.toRequest(new LoincImportRequest().setVersion("2.80"), concepts)
    def answerList = request.properties.find { it.name == "answer-list" }
    def classProp = request.properties.find { it.name == "CLASS" }

    then: "the Coding property records the external code system on its rule (issue #48)"
    answerList.rule != null
    answerList.rule.codeSystems == ["answer-list"]

    and: "a plain string property carries no binding"
    classProp != null
    classProp.rule == null
  }

  // Issue #48 Bug 2. processLinguisticVariants() merges a translation file into each concept's display
  // map under its language (e.g. "cs"), so cs designations are imported; the importer now also declares
  // those languages on the code system / version (previously hardcoded to [en]), which is what made the
  // Czech designations render correctly only after a manual re-save.
  def "(#48 bug2) importing a translation language declares it as a supported language"() {
    given: "a concept carrying English + Czech displays, exactly as processLinguisticVariants leaves it"
    def concept = new LoincConcept().setCode("1234-5").setDisplay([en: "Glucose", cs: "Glukóza"]).setProperties([])

    when:
    def request = LoincMapper.toRequest(new LoincImportRequest().setVersion("2.80").setLanguage("cs"), [concept])

    then: "the Czech designation is imported"
    request.concepts.first().designations.any { it.language == "cs" && it.designationType == "display" }

    and: "and cs must be declared supported on the code system and version (today it is NOT — the bug)"
    request.codeSystem.supportedLanguages.contains("cs")
    request.version.supportedLanguages.contains("cs")
  }

  private static LoincConcept loincConcept(String code, String answerCode, String answerSystem) {
    return new LoincConcept()
        .setCode(code)
        .setDisplay([en: "display " + code])
        .setProperties([
            new LoincConceptProperty().setName("answer-list").setType(EntityPropertyType.coding)
                .setValue(new Concept().setCode(answerCode).setCodeSystem(answerSystem)),
            new LoincConceptProperty().setName("CLASS").setType(EntityPropertyType.string).setValue("CHEM"),
        ])
  }
}
