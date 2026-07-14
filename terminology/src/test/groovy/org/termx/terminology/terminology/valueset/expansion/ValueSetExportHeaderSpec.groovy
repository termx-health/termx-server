package org.termx.terminology.terminology.valueset.expansion

import org.termx.core.sys.lorque.LorqueProcessService
import org.termx.terminology.terminology.codesystem.concept.ConceptService
import org.termx.terminology.terminology.valueset.version.ValueSetVersionService
import org.termx.ts.codesystem.Designation
import org.termx.ts.valueset.ValueSetVersion
import org.termx.ts.valueset.ValueSetVersionConcept
import org.termx.ts.valueset.ValueSetVersionRuleSet
import spock.lang.Specification

class ValueSetExportHeaderSpec extends Specification {
  def lorqueProcessService = Mock(LorqueProcessService)
  def valueSetVersionService = Mock(ValueSetVersionService)
  def conceptService = Mock(ConceptService)

  def service = new ValueSetExportService(lorqueProcessService, valueSetVersionService, conceptService)

  def "composeHeaders keeps additional designations in their own designation-type-aware columns"() {
    given:
    def concept = new ValueSetVersionConcept()
        .setConcept(new ValueSetVersionConcept.ValueSetVersionConceptValue().setCode("c1"))
        .setDisplay(new Designation().setName("Display EN").setLanguage("en"))
        .setAdditionalDesignations([
            new Designation().setName("Synonym EN").setLanguage("en").setDesignationType("synonym"),
            new Designation().setName("Extra display EN").setLanguage("en").setDesignationType("display")
        ])
    def vsv = new ValueSetVersion().setRuleSet(new ValueSetVersionRuleSet().setRules([]))

    when:
    def headers = service.composeHeaders([concept], vsv)

    then:
    headers.contains("synonym#en")
    headers.contains("display#en")
    !headers.contains("additionalDesignation#en")
  }

  def "composeRow emits each designation type into its own column, display-typed merged into display"() {
    given:
    def concept = new ValueSetVersionConcept()
        .setConcept(new ValueSetVersionConcept.ValueSetVersionConceptValue().setCode("c1"))
        .setDisplay(new Designation().setName("Display EN").setLanguage("en"))
        .setAdditionalDesignations([
            new Designation().setName("Synonym EN").setLanguage("en").setDesignationType("synonym"),
            new Designation().setName("Extra display EN").setLanguage("en").setDesignationType("display")
        ])
    def vsv = new ValueSetVersion().setRuleSet(new ValueSetVersionRuleSet().setRules([]))
    def headers = service.composeHeaders([concept], vsv)

    when:
    def row = service.composeRow(concept, headers, [:])

    then:
    def synonymValue = row[headers.indexOf("synonym#en")]
    def displayValue = row[headers.indexOf("display#en")]
    synonymValue == "Synonym EN"
    displayValue.contains("Display EN")
    displayValue.contains("Extra display EN")
  }
}
