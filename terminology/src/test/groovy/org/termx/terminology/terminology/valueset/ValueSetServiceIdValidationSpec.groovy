package org.termx.terminology.terminology.valueset

import com.kodality.commons.exception.ApiException
import org.termx.terminology.terminology.valueset.ruleset.ValueSetVersionRuleService
import org.termx.terminology.terminology.valueset.version.ValueSetVersionService
import org.termx.ts.valueset.ValueSet
import spock.lang.Specification

/**
 * Migrated from tehik fork KL-104: save() must reject a resource id that does not match
 * the FHIR id regex [A-Za-z0-9\-.]{1,64}, mirroring CodeSystemService (TE119).
 */
class ValueSetServiceIdValidationSpec extends Specification {
  def repository = Mock(ValueSetRepository)
  def versionService = Mock(ValueSetVersionService)
  def ruleService = Mock(ValueSetVersionRuleService)
  def service = new ValueSetService(repository, versionService, ruleService)

  def "save rejects an id violating the resource-id regex"() {
    when:
    service.save(new ValueSet().setId("bad id"))

    then:
    def e = thrown(ApiException)
    e.issues*.code.contains("TE119")
    0 * repository.save(_)
  }

  def "save accepts a valid id"() {
    when:
    service.save(new ValueSet().setId("valid-id.1"))

    then:
    1 * repository.save(_)
  }
}
