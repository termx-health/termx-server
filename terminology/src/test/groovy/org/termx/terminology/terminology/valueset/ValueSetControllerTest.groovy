package org.termx.terminology.terminology.valueset

import org.termx.core.sys.job.logger.ImportLogger
import org.termx.core.sys.lorque.LorqueProcessService
import org.termx.terminology.terminology.valueset.expansion.ValueSetExportService
import org.termx.terminology.terminology.valueset.expansion.ValueSetRuleExpandService
import org.termx.terminology.terminology.valueset.expansion.ValueSetVersionConceptService
import org.termx.terminology.terminology.valueset.provenance.ValueSetProvenanceService
import org.termx.terminology.terminology.valueset.ruleset.ValueSetVersionRuleService
import org.termx.terminology.terminology.valueset.ruleset.ValueSetVersionRuleSetService
import org.termx.terminology.terminology.valueset.version.ValueSetVersionService
import org.termx.ts.valueset.ValueSet
import spock.lang.Specification

class ValueSetControllerTest extends Specification {
  def valueSetService = Mock(ValueSetService)
  def valueSetVersionService = Mock(ValueSetVersionService)
  def valueSetVersionRuleService = Mock(ValueSetVersionRuleService)
  def valueSetVersionRuleSetService = Mock(ValueSetVersionRuleSetService)
  def valueSetVersionConceptService = Mock(ValueSetVersionConceptService)
  def valueSetDuplicateService = Mock(ValueSetDuplicateService)
  def valueSetRuleExpandService = Mock(ValueSetRuleExpandService)
  def importLogger = Mock(ImportLogger)
  def provenanceService = Mock(ValueSetProvenanceService)
  def lorqueProcessService = Mock(LorqueProcessService)
  def valueSetExportService = Mock(ValueSetExportService)

  def controller = new ValueSetController(
      valueSetService,
      valueSetVersionService,
      valueSetVersionRuleService,
      valueSetVersionRuleSetService,
      valueSetVersionConceptService,
      valueSetDuplicateService,
      valueSetRuleExpandService,
      importLogger,
      provenanceService,
      lorqueProcessService,
      valueSetExportService
  )

  def "getValueSet defaults to undecorated load"() {
    given:
    def valueSet = new ValueSet().setId("administrative-gender")

    when:
    def result = controller.getValueSet("administrative-gender", Optional.empty())

    then:
    1 * valueSetService.load("administrative-gender", false) >> Optional.of(valueSet)
    result.is(valueSet)
  }

  def "getValueSet forwards explicit decorate flag"() {
    given:
    def valueSet = new ValueSet().setId("administrative-gender")

    when:
    def result = controller.getValueSet("administrative-gender", Optional.of(true))

    then:
    1 * valueSetService.load("administrative-gender", true) >> Optional.of(valueSet)
    result.is(valueSet)
  }
}
