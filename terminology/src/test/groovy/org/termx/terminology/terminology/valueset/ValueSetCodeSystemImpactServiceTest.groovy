package org.termx.terminology.terminology.valueset

import com.kodality.commons.model.QueryResult
import org.termx.terminology.terminology.valueset.expansion.ValueSetCodeSystemVersionResolver
import org.termx.terminology.terminology.valueset.expansion.ValueSetVersionConceptService
import org.termx.terminology.terminology.valueset.version.ValueSetVersionService
import org.termx.ts.codesystem.CodeSystemVersion
import org.termx.ts.codesystem.CodeSystemVersionReference
import org.termx.ts.valueset.ValueSetVersion
import org.termx.ts.valueset.ValueSetVersionQueryParams
import org.termx.ts.valueset.ValueSetVersionRuleSet
import org.termx.ts.valueset.ValueSetVersionRuleSet.ValueSetVersionRule
import spock.lang.Specification

class ValueSetCodeSystemImpactServiceTest extends Specification {
  def valueSetVersionService = Mock(ValueSetVersionService)
  def valueSetVersionConceptService = Mock(ValueSetVersionConceptService)
  def codeSystemVersionResolver = Mock(ValueSetCodeSystemVersionResolver)

  def service = new ValueSetCodeSystemImpactService(valueSetVersionService, valueSetVersionConceptService, codeSystemVersionResolver)

  def "findValueSetImpacts returns one row per value set version"() {
    given:
    def currentVersion = new CodeSystemVersion().setId(200L).setVersion("6.0.1")
    def oldVersion = new CodeSystemVersion().setId(100L).setVersion("6.0.0")
    def firstRule = new ValueSetVersionRule()
        .setId(1L)
        .setType("include")
        .setCodeSystem("administrative-gender")
        .setCodeSystemVersion(new CodeSystemVersionReference().setId(100L).setVersion("6.0.0"))
    def secondRule = new ValueSetVersionRule()
        .setId(2L)
        .setType("include")
        .setCodeSystem("administrative-gender")
        .setCodeSystemVersion(new CodeSystemVersionReference().setId(200L).setVersion("6.0.1"))
    def version = new ValueSetVersion()
        .setValueSet("vs-gender")
        .setVersion("1.0.0")
        .setRuleSet(new ValueSetVersionRuleSet().setRules([firstRule, secondRule]))

    when:
    def impacts = service.findValueSetImpacts("administrative-gender")

    then:
    1 * valueSetVersionService.query(_ as ValueSetVersionQueryParams) >> new QueryResult([version])
    2 * codeSystemVersionResolver.resolve("administrative-gender", null) >> currentVersion
    1 * codeSystemVersionResolver.isDynamic(firstRule) >> false
    1 * codeSystemVersionResolver.isDynamic(secondRule) >> false
    1 * codeSystemVersionResolver.resolve("administrative-gender", firstRule.codeSystemVersion) >> oldVersion
    1 * codeSystemVersionResolver.resolve("administrative-gender", secondRule.codeSystemVersion) >> currentVersion
    4 * codeSystemVersionResolver.copyReference(_ as CodeSystemVersion) >> { CodeSystemVersion source ->
      new CodeSystemVersionReference().setId(source.id).setVersion(source.version)
    }

    impacts.size() == 1
    impacts.first().artifactId == "vs-gender"
    impacts.first().artifactVersion == "1.0.0"
    impacts.first().affected
    impacts.first().resolvedCodeSystemVersion.version == "6.0.0"
    impacts.first().currentCodeSystemVersion.version == "6.0.1"
  }
}
