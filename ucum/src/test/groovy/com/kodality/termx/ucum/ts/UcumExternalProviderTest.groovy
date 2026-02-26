package com.kodality.termx.ucum.ts

import com.kodality.termx.ts.codesystem.ConceptQueryParams
import com.kodality.termx.ts.property.PropertyReference
import com.kodality.termx.ts.valueset.ValueSetVersionConcept
import com.kodality.termx.ts.valueset.ValueSetVersionRuleSet.ValueSetVersionRule
import org.termx.ucum.dto.BaseUnitDto
import org.termx.ucum.dto.DefinedUnitDto
import org.termx.ucum.dto.ValidateResponseDto
import org.termx.ucum.service.UcumService
import spock.lang.Specification

class UcumExternalProviderTest extends Specification {
  def ucumService = Mock(UcumService)

  def "codesystem provider resolves valid UCUM expression via ucum validation"() {
    given:
    ucumService.getBaseUnits() >> []
    ucumService.getDefinedUnits() >> []
    ucumService.validate("mg/(24.h)") >> valid(true)
    def provider = provider()

    when:
    def result = provider.searchConcepts(new ConceptQueryParams().setCodeSystem("ucum").setCodeEq("mg/(24.h)"))

    then:
    result.data*.code == ["mg/(24.h)"]
    result.data.first().versions.first().designations*.name == ["mg/(24.h)"]
  }

  def "codesystem provider rejects invalid UCUM expression"() {
    given:
    ucumService.getBaseUnits() >> []
    ucumService.getDefinedUnits() >> []
    ucumService.validate("not-a-ucum-code") >> valid(false)
    def provider = provider()

    when:
    def result = provider.searchConcepts(new ConceptQueryParams().setCodeSystem("ucum").setCodeEq("not-a-ucum-code"))

    then:
    result.data.isEmpty()
  }

  def "valueset expand kind filter uses ucum model properties"() {
    given:
    ucumService.getBaseUnits() >> [baseUnit("g", "mass", ["gram"])]
    ucumService.getDefinedUnits() >> [definedUnit("L", "volume", ["liter"])]
    def expandProvider = expandProvider()
    def rule = new ValueSetVersionRule()
        .setCodeSystem("ucum")
        .setType("include")
        .setFilters([
            new ValueSetVersionRule.ValueSetRuleFilter()
                .setProperty(new PropertyReference().setName("kind"))
                .setOperator("=")
                .setValue("volume")
        ])

    when:
    def result = expandProvider.ruleExpand(rule, null, null)

    then:
    result*.concept*.code.flatten() == ["L"]
  }

  def "valueset expand decorates explicit ucum expressions with display"() {
    given:
    ucumService.getBaseUnits() >> []
    ucumService.getDefinedUnits() >> []
    ucumService.validate("mg/(24.h)") >> valid(true)
    def expandProvider = expandProvider()
    def rule = new ValueSetVersionRule()
        .setCodeSystem("ucum")
        .setType("include")
        .setConcepts([
            new ValueSetVersionConcept().setConcept(
                new ValueSetVersionConcept.ValueSetVersionConceptValue()
                    .setCode("mg/(24.h)")
                    .setCodeSystem("ucum"))
        ])

    when:
    def result = expandProvider.ruleExpand(rule, null, null)

    then:
    result.size() == 1
    result.first().active
    result.first().additionalDesignations*.name == ["mg/(24.h)"]
  }

  private UcumCodeSystemProvider provider() {
    new UcumCodeSystemProvider(new UcumConceptResolver(new UcumMapper(), ucumService))
  }

  private UcumValueSetExpandProvider expandProvider() {
    new UcumValueSetExpandProvider(new UcumConceptResolver(new UcumMapper(), ucumService))
  }

  private static ValidateResponseDto valid(boolean isValid) {
    def dto = new ValidateResponseDto()
    dto.setValid(isValid)
    return dto
  }

  private static DefinedUnitDto definedUnit(String code, String property, List<String> names) {
    def dto = new DefinedUnitDto()
    dto.setCode(code)
    dto.setProperty(property)
    dto.setNames(names)
    return dto
  }

  private static BaseUnitDto baseUnit(String code, String property, List<String> names) {
    def dto = new BaseUnitDto()
    dto.setCode(code)
    dto.setProperty(property)
    dto.setNames(names)
    return dto
  }
}
