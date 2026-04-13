package org.termx.terminology.terminology.codesystem

import com.kodality.commons.model.QueryResult
import org.termx.terminology.terminology.codesystem.concept.ConceptService
import org.termx.terminology.terminology.codesystem.entityproperty.EntityPropertyService
import org.termx.terminology.terminology.codesystem.version.CodeSystemVersionService
import org.termx.terminology.terminology.valueset.ValueSetService
import org.termx.ts.codesystem.CodeSystem
import org.termx.ts.codesystem.CodeSystemQueryParams
import spock.lang.Specification

class CodeSystemServiceTest extends Specification {
  def repository = Mock(CodeSystemRepository)
  def conceptService = Mock(ConceptService)
  def entityPropertyService = Mock(EntityPropertyService)
  def codeSystemVersionService = Mock(CodeSystemVersionService)
  def valueSetService = Mock(ValueSetService)

  def service = new CodeSystemService(repository, conceptService, entityPropertyService, codeSystemVersionService, valueSetService)

  def "query preserves lastVersionDecorated without version decoration"() {
    given:
    def params = new CodeSystemQueryParams()
        .setLastVersionDecorated(true)
        .setVersionsDecorated(false)

    when:
    def result = service.query(params)

    then:
    1 * repository.query({
      it.is(params) && it.lastVersionDecorated && !it.versionsDecorated
    }) >> new QueryResult([new CodeSystem().setId("administrative-gender")])
    0 * codeSystemVersionService.query(_)
    result.data*.id == ["administrative-gender"]
  }
}
