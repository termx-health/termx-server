package org.termx.terminology.terminology.valueset.ruleset

import com.kodality.commons.exception.ApiException
import org.termx.ts.property.PropertyReference
import org.termx.ts.valueset.ValueSetVersionRuleSet
import org.termx.ts.valueset.ValueSetVersionRuleSet.ValueSetVersionRule
import org.termx.ts.valueset.ValueSetVersionRuleSet.ValueSetVersionRule.ValueSetRuleFilter
import spock.lang.Specification

class ValueSetVersionRuleServiceTest extends Specification {
  def repository = Mock(ValueSetVersionRuleRepository)
  def ruleSetService = Mock(ValueSetVersionRuleSetService)
  def service = new ValueSetVersionRuleService(repository, ruleSetService)

  def "save(rule) rejects an unknown filter operator before persisting"() {
    given:
    def rule = new ValueSetVersionRule().setType("include").setCodeSystem("cs")
        .setFilters([filter("concept", "is-a-typo", "A")])

    when:
    service.save(rule, "vs", "1.0.0")

    then:
    def e = thrown(ApiException)
    e.issues*.code.contains("TE310")
    0 * repository.save(_, _)
  }

  def "save(rule) rejects a filter with a null operator"() {
    given:
    def rule = new ValueSetVersionRule().setType("include").setCodeSystem("cs")
        .setFilters([filter("concept", null, "A")])

    when:
    service.save(rule, "vs", "1.0.0")

    then:
    thrown(ApiException)
    0 * repository.save(_, _)
  }

  def "save(rule) accepts every supported FHIR filter operator"() {
    given:
    ruleSetService.load("vs", "1.0.0") >> Optional.of(new ValueSetVersionRuleSet().setId(1L))
    def rule = new ValueSetVersionRule().setType("include").setCodeSystem("cs")
        .setFilters([filter("concept", operator, "A")])

    when:
    service.save(rule, "vs", "1.0.0")

    then:
    1 * repository.save(rule, 1L)

    where:
    operator << ["=", "is-a", "descendent-of", "child-of", "descendent-leaf",
                 "is-not-a", "regex", "in", "not-in", "generalizes", "exists"]
  }

  def "bulk save validates each rule's filter operators"() {
    given:
    ruleSetService.load("vs", "1.0.0") >> Optional.of(new ValueSetVersionRuleSet().setId(1L))
    def rules = [new ValueSetVersionRule().setType("include").setCodeSystem("cs")
                     .setFilters([filter("concept", "bogus", "A")])]

    when:
    service.save(rules, "vs", "1.0.0")

    then:
    thrown(ApiException)
    0 * repository.save(_, _)
  }

  private static ValueSetRuleFilter filter(String property, String operator, Object value) {
    return new ValueSetRuleFilter()
        .setProperty(new PropertyReference().setName(property))
        .setOperator(operator)
        .setValue(value)
  }
}
