package org.termx.terminology.terminology.valueset.version

import org.termx.terminology.terminology.valueset.ruleset.ValueSetVersionRuleSetService
import org.termx.ts.PublicationStatus
import org.termx.ts.valueset.ValueSetVersion
import org.termx.ts.valueset.ValueSetVersionRuleSet
import spock.lang.Specification

/**
 * The status of a stored version changes only through activate / retire / draft. A version save took the status
 * from the request, so a save that named a published version "draft" turned it back into a draft and saved its
 * rule set — with the edit privilege alone, and past the check that rules of a published version do not change.
 */
class ValueSetVersionServiceStatusSpec extends Specification {
  def repository = Mock(ValueSetVersionRepository)
  def ruleSetService = Mock(ValueSetVersionRuleSetService)
  def service = new ValueSetVersionService(repository, ruleSetService)

  def "a save that calls a #stored version a draft neither changes its status nor saves its rule set"() {
    given:
    repository.load(5L) >> version(stored)
    def request = version(PublicationStatus.draft)
    request.ruleSet = new ValueSetVersionRuleSet(id: 10L, inactive: true)

    when:
    service.save(request)

    then:
    request.status == stored
    1 * repository.saveExpirationDate(request)
    0 * repository.save(_)
    0 * ruleSetService.save(*_)

    where:
    stored << [PublicationStatus.active, PublicationStatus.retired]
  }

  def "a draft version is saved with its rule set as before"() {
    given:
    repository.load(5L) >> version(PublicationStatus.draft)
    repository.query(_) >> new com.kodality.commons.model.QueryResult([])
    def request = version(PublicationStatus.draft)
    request.ruleSet = new ValueSetVersionRuleSet(id: 10L)

    when:
    service.save(request)

    then:
    1 * repository.save(request)
    1 * ruleSetService.save(request.ruleSet, "vs", "1.0")
  }

  private static ValueSetVersion version(String status) {
    def v = new ValueSetVersion()
    v.id = 5L
    v.valueSet = "vs"
    v.version = "1.0"
    v.status = status
    return v
  }
}
