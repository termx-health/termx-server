package org.termx.terminology.terminology.codesystem.concept

import com.kodality.commons.model.QueryResult
import org.termx.terminology.terminology.codesystem.CodeSystemRepository
import org.termx.terminology.terminology.codesystem.entity.CodeSystemEntityVersionService
import org.termx.terminology.terminology.codesystem.version.CodeSystemVersionService
import org.termx.ts.codesystem.CodeSystem
import org.termx.ts.codesystem.CodeSystemContent
import org.termx.ts.codesystem.CodeSystemQueryParams
import org.termx.ts.codesystem.CodeSystemVersion
import org.termx.ts.codesystem.CodeSystemVersionQueryParams
import org.termx.ts.codesystem.Concept
import org.termx.ts.codesystem.ConceptQueryParams
import spock.lang.Specification

class ConceptSupplementServiceTest extends Specification {
  def conceptRepository = Mock(ConceptRepository)
  def codeSystemRepository = Mock(CodeSystemRepository)
  def codeSystemVersionService = Mock(CodeSystemVersionService)
  def codeSystemEntityVersionService = Mock(CodeSystemEntityVersionService)

  def service = new ConceptSupplementService(conceptRepository, codeSystemRepository, codeSystemVersionService, codeSystemEntityVersionService)

  def "mergeRuntimeSupplements resolves supplements without CodeSystemService"() {
    given:
    def params = new ConceptQueryParams()
        .setUseSupplement("https://termx.org/fhir/CodeSystem/ucum-supplement-lt")
        .setDisplayLanguage("lt")
        .setIncludeSupplement(false)
    def concepts = [new Concept().setCodeSystem("ucum").setCode("mg")]
    def supplement = new CodeSystem()
        .setId("ucum-supplement-lt")
        .setBaseCodeSystem("ucum")
        .setContent(CodeSystemContent.supplement)
    def supplementVersion = new CodeSystemVersion().setVersion("2026")

    when:
    def result = service.mergeRuntimeSupplements(concepts, params)

    then:
    result.is(concepts)
    1 * codeSystemRepository.query({
      it instanceof CodeSystemQueryParams &&
          it.uri == "https://termx.org/fhir/CodeSystem/ucum-supplement-lt" &&
          it.limit == 1
    }) >> new QueryResult([supplement])
    1 * codeSystemVersionService.query({
      it instanceof CodeSystemVersionQueryParams &&
          it.codeSystem == "ucum-supplement-lt" &&
          it.status == "active"
    }) >> new QueryResult([supplementVersion])
    1 * conceptRepository.query({ it.codeSystem == "ucum-supplement-lt" && it.codeSystemVersion == "2026" && it.codes == ["mg"] }) >> new QueryResult([])
    0 * codeSystemEntityVersionService._
  }
}
