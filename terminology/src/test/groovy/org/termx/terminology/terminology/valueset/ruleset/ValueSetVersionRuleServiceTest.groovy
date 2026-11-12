package org.termx.terminology.terminology.valueset.ruleset

import com.kodality.commons.exception.ApiClientException
import org.termx.terminology.terminology.valueset.version.ValueSetVersionRepository
import org.termx.ts.PublicationStatus
import org.termx.ts.valueset.ValueSetVersion
import org.termx.ts.valueset.ValueSetVersionRuleSet
import org.termx.ts.valueset.ValueSetVersionRuleSet.ValueSetVersionRule
import spock.lang.Specification

/**
 * Rules of a published value set version could still be changed through the rule endpoints and the value set
 * transaction, while its expansion stayed as it was. A rule id was also not checked against the value set in the
 * path, so an editor of one value set could change or delete a rule of another.
 */
class ValueSetVersionRuleServiceTest extends Specification {
  static final Long RULE_SET = 10L

  def versionRepository = Mock(ValueSetVersionRepository)
  def ruleSetRepository = Mock(ValueSetVersionRuleSetRepository)
  def ruleRepository = Mock(ValueSetVersionRuleRepository)
  def ruleSetService = new ValueSetVersionRuleSetService(ruleSetRepository, versionRepository, ruleRepository)
  def service = new ValueSetVersionRuleService(ruleRepository, ruleSetService)

  def setup() {
    ruleSetRepository.load("vs", "1.0") >> ruleSet()
    ruleSetRepository.load(5L) >> ruleSet()
    ruleRepository.belongTo({ it.every { id -> id in [1L, 2L] } }, RULE_SET) >> true
    ruleRepository.belongTo(_, _) >> false
  }

  def "a rule of a #status version cannot be added, changed or deleted"() {
    given:
    versionRepository.load("vs", "1.0") >> version(status)

    when:
    action(service)

    then:
    def e = thrown(ApiClientException)
    e.issues*.code == ["TE122"]
    0 * ruleRepository.save(*_)
    0 * ruleRepository.delete(*_)
    0 * ruleRepository.retain(*_)

    where:
    [status, action] << [[PublicationStatus.active, PublicationStatus.retired], [
        { ValueSetVersionRuleService s -> s.save(new ValueSetVersionRule(type: "include", codeSystem: "cs"), "vs", "1.0") },
        { ValueSetVersionRuleService s -> s.save(new ValueSetVersionRule(id: 1L, type: "include", codeSystem: "cs"), "vs", "1.0") },
        { ValueSetVersionRuleService s -> s.save([new ValueSetVersionRule(id: 1L, type: "include", codeSystem: "cs")], "vs", "1.0") },
        { ValueSetVersionRuleService s -> s.delete(1L, "vs", "1.0") },
    ]].combinations()
  }

  def "the rule set of a published version cannot be changed"() {
    given:
    versionRepository.load("vs", "1.0") >> version(PublicationStatus.active)

    when:
    ruleSetService.save(new ValueSetVersionRuleSet(id: RULE_SET, inactive: true), "vs", "1.0")

    then:
    def e = thrown(ApiClientException)
    e.issues*.code == ["TE122"]
    0 * ruleSetRepository.save(*_)
  }

  def "the rule set of another version cannot be taken over by naming its id"() {
    given:
    versionRepository.load("vs", "1.0") >> version(PublicationStatus.draft)

    when:
    ruleSetService.save(new ValueSetVersionRuleSet(id: 99L), "vs", "1.0")

    then:
    def e = thrown(ApiClientException)
    e.issues*.code == ["TE124"]
    0 * ruleSetRepository.save(*_)
  }

  def "a version that has no rule set yet cannot take over another version's rule set"() {
    given:
    def version = version(PublicationStatus.draft)
    version.id = 6L
    versionRepository.load("vs", "2.0") >> version

    when:
    ruleSetService.save(new ValueSetVersionRuleSet(id: RULE_SET), "vs", "2.0")

    then:
    def e = thrown(ApiClientException)
    e.issues*.code == ["TE124"]
    0 * ruleSetRepository.save(*_)
  }

  def "a draft version's own rule set, or a new one, is saved as before"() {
    given:
    versionRepository.load("vs", "1.0") >> version(PublicationStatus.draft)

    when:
    ruleSetService.save(new ValueSetVersionRuleSet(id: RULE_SET, inactive: true), "vs", "1.0")
    ruleSetService.save(new ValueSetVersionRuleSet(), "vs", "1.0")

    then:
    2 * ruleSetRepository.save(_, 5L)
  }

  def "a rule of another value set cannot be changed or deleted through this one"() {
    given:
    versionRepository.load("vs", "1.0") >> version(PublicationStatus.draft)

    when:
    action(service)

    then:
    def e = thrown(ApiClientException)
    e.issues*.code == ["TE123"]
    0 * ruleRepository.save(*_)
    0 * ruleRepository.delete(*_)
    0 * ruleRepository.retain(*_)

    where:
    action << [
        { ValueSetVersionRuleService s -> s.save(new ValueSetVersionRule(id: 99L, type: "include", codeSystem: "cs"), "vs", "1.0") },
        { ValueSetVersionRuleService s -> s.save([new ValueSetVersionRule(id: 1L, type: "include"), new ValueSetVersionRule(id: 99L, type: "include")], "vs", "1.0") },
        { ValueSetVersionRuleService s -> s.delete(99L, "vs", "1.0") },
    ]
  }

  def "rules of a draft version are saved and deleted as before"() {
    given:
    versionRepository.load("vs", "1.0") >> version(PublicationStatus.draft)

    when:
    service.save(new ValueSetVersionRule(type: "include", codeSystem: "cs"), "vs", "1.0")
    service.save(new ValueSetVersionRule(id: 1L, type: "include", codeSystem: "cs"), "vs", "1.0")
    service.save([new ValueSetVersionRule(id: 2L, type: "include"), new ValueSetVersionRule(type: "exclude")], "vs", "1.0")
    service.delete(1L, "vs", "1.0")

    then:
    4 * ruleRepository.save(_, RULE_SET)
    1 * ruleRepository.retain(_, RULE_SET)
    1 * ruleRepository.delete(1L)
  }

  private static ValueSetVersion version(String status) {
    def v = new ValueSetVersion()
    v.id = 5L
    v.valueSet = "vs"
    v.version = "1.0"
    v.status = status
    return v
  }

  private static ValueSetVersionRuleSet ruleSet() {
    def rs = new ValueSetVersionRuleSet()
    rs.id = RULE_SET
    return rs
  }
}
