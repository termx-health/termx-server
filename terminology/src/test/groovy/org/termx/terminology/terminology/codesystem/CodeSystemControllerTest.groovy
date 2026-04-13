package org.termx.terminology.terminology.codesystem

import org.termx.core.sys.lorque.LorqueProcessService
import org.termx.terminology.terminology.codesystem.association.CodeSystemAssociationService
import org.termx.terminology.terminology.codesystem.concept.ConceptExportService
import org.termx.terminology.terminology.codesystem.concept.ConceptService
import org.termx.terminology.terminology.codesystem.entity.CodeSystemEntityVersionService
import org.termx.terminology.terminology.codesystem.entity.CodeSystemSupplementService
import org.termx.terminology.terminology.codesystem.entityproperty.EntityPropertyService
import org.termx.terminology.terminology.codesystem.version.CodeSystemVersionService
import org.termx.terminology.terminology.valueset.ValueSetCodeSystemImpactService
import org.termx.ts.codesystem.CodeSystem
import spock.lang.Specification

class CodeSystemControllerTest extends Specification {
  def conceptService = Mock(ConceptService)
  def conceptExportService = Mock(ConceptExportService)
  def codeSystemService = Mock(CodeSystemService)
  def entityPropertyService = Mock(EntityPropertyService)
  def codeSystemVersionService = Mock(CodeSystemVersionService)
  def codeSystemDuplicateService = Mock(CodeSystemDuplicateService)
  def codeSystemAssociationService = Mock(CodeSystemAssociationService)
  def codeSystemEntityVersionService = Mock(CodeSystemEntityVersionService)
  def codeSystemSupplementService = Mock(CodeSystemSupplementService)
  def provenanceService = Mock(CodeSystemProvenanceService)
  def lorqueProcessService = Mock(LorqueProcessService)
  def valueSetCodeSystemImpactService = Mock(ValueSetCodeSystemImpactService)

  def controller = new CodeSystemController(
      conceptService,
      conceptExportService,
      codeSystemService,
      entityPropertyService,
      codeSystemVersionService,
      codeSystemDuplicateService,
      codeSystemAssociationService,
      codeSystemEntityVersionService,
      codeSystemSupplementService,
      provenanceService,
      lorqueProcessService,
      valueSetCodeSystemImpactService
  )

  def "getCodeSystem defaults to undecorated load"() {
    given:
    def codeSystem = new CodeSystem().setId("administrative-gender")

    when:
    def result = controller.getCodeSystem("administrative-gender", Optional.empty())

    then:
    1 * codeSystemService.load("administrative-gender", false) >> Optional.of(codeSystem)
    result.is(codeSystem)
  }

  def "getCodeSystem forwards explicit decorate flag"() {
    given:
    def codeSystem = new CodeSystem().setId("administrative-gender")

    when:
    def result = controller.getCodeSystem("administrative-gender", Optional.of(true))

    then:
    1 * codeSystemService.load("administrative-gender", true) >> Optional.of(codeSystem)
    result.is(codeSystem)
  }
}
