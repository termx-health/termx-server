package org.termx.terminology.terminology.valueset

import com.kodality.commons.model.QueryResult
import org.termx.terminology.terminology.valueset.ruleset.ValueSetVersionRuleService
import org.termx.terminology.terminology.valueset.version.ValueSetVersionService
import org.termx.ts.valueset.ValueSet
import org.termx.ts.valueset.ValueSetQueryParams
import spock.lang.Specification

class ValueSetServiceTest extends Specification {
  def repository = Mock(ValueSetRepository)
  def valueSetVersionService = Mock(ValueSetVersionService)
  def valueSetVersionRuleService = Mock(ValueSetVersionRuleService)

  def service = new ValueSetService(repository, valueSetVersionService, valueSetVersionRuleService)

  def "query preserves lastVersionDecorated without full decoration"() {
    given:
    def params = new ValueSetQueryParams()
        .setLastVersionDecorated(true)
        .setDecorated(false)

    when:
    def result = service.query(params)

    then:
    1 * repository.query({
      it.is(params) && it.lastVersionDecorated && !it.decorated
    }) >> new QueryResult([new ValueSet().setId("administrative-gender")])
    0 * valueSetVersionService.query(_)
    result.data*.id == ["administrative-gender"]
  }
}
