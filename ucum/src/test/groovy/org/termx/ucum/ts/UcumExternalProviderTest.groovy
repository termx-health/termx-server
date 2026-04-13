package org.termx.ucum.ts

import com.kodality.commons.model.QueryResult
import org.termx.terminology.terminology.codesystem.CodeSystemRepository
import org.termx.terminology.terminology.codesystem.concept.ConceptRepository
import org.termx.terminology.terminology.codesystem.designation.DesignationService
import org.termx.terminology.terminology.codesystem.entity.CodeSystemEntityVersionRepository
import org.termx.terminology.terminology.codesystem.version.CodeSystemVersionRepository
import org.termx.ts.codesystem.CodeSystem
import org.termx.ts.codesystem.CodeSystemEntityVersion
import org.termx.ts.codesystem.CodeSystemEntityVersionQueryParams
import org.termx.ts.codesystem.CodeSystemQueryParams
import org.termx.ts.codesystem.CodeSystemVersion
import org.termx.ts.codesystem.CodeSystemVersionQueryParams
import org.termx.ts.codesystem.Concept
import org.termx.ts.codesystem.ConceptQueryParams
import org.termx.ts.codesystem.Designation
import org.termx.ts.codesystem.DesignationQueryParams
import org.termx.ts.property.PropertyReference
import org.termx.ts.valueset.ValueSetVersionConcept
import org.termx.ts.valueset.ValueSetVersionRuleSet.ValueSetVersionRule
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

  def "codesystem provider text search matches supplement designations"() {
    given:
    ucumService.getBaseUnits() >> []
    ucumService.getDefinedUnits() >> [definedUnit("mL", "volume", ["milliliter"])]
    def provider = provider([
        "mL": [designation("display", "lt", "Mililitras", false, true)]
    ])

    when:
    def result = provider.searchConcepts(new ConceptQueryParams()
        .setCodeSystem("ucum")
        .setTextContains("Mililitras")
        .setIncludeSupplement(true)
        .setDisplayLanguage("lt"))

    then:
    result.data*.code == ["mL"]
  }

  def "codesystem provider text search finds supplement-only ucum expressions"() {
    given:
    ucumService.getBaseUnits() >> []
    ucumService.getDefinedUnits() >> []
    ucumService.validate("mg/(24.h)") >> valid(true)
    def provider = provider([
        "mg/(24.h)": [designation("display", "lt", "Miligramai per 24 val", false, true)]
    ])

    when:
    def result = provider.searchConcepts(new ConceptQueryParams()
        .setCodeSystem("ucum")
        .setTextContains("Miligramai per 24 val")
        .setIncludeSupplement(true)
        .setDisplayLanguage("lt"))

    then:
    result.data*.code == ["mg/(24.h)"]
  }

  def "resolver invalidation clears cached supplement-only searchable units"() {
    given:
    ucumService.getBaseUnits() >> []
    ucumService.getDefinedUnits() >> []
    ucumService.validate("mg/(24.h)") >> valid(true)
    def cachedUnits = [new UcumUnitDefinition().setCode("mg/(24.h)").setNames(["Miligramai per 24 val"])]
    def resolver = new UcumConceptResolver(new UcumMapper(), ucumService, supplementServiceForCachedUnits(cachedUnits))

    expect:
    resolver.search(new ConceptQueryParams().setTextContains("Miligramai per 24 val")).data*.code == ["mg/(24.h)"]

    when:
    cachedUnits.clear()

    then:
    resolver.search(new ConceptQueryParams().setTextContains("Miligramai per 24 val")).data*.code == ["mg/(24.h)"]

    when:
    resolver.invalidateCache()

    then:
    resolver.search(new ConceptQueryParams().setTextContains("Miligramai per 24 val")).data*.code.isEmpty()
  }

  def "resolver reloads cached supplement-only searchable units after ttl expires"() {
    given:
    ucumService.getBaseUnits() >> []
    ucumService.getDefinedUnits() >> []
    ucumService.validate("mg/(24.h)") >> valid(true)
    def cachedUnits = [new UcumUnitDefinition().setCode("mg/(24.h)").setNames(["Miligramai per 24 val"])]
    def resolver = new UcumConceptResolver(new UcumMapper(), ucumService, supplementServiceForCachedUnits(cachedUnits))
    resolver.@cacheTtlMillis = 1L

    expect:
    resolver.search(new ConceptQueryParams().setTextContains("Miligramai per 24 val")).data*.code == ["mg/(24.h)"]

    when:
    cachedUnits.clear()
    sleep(10)

    then:
    resolver.search(new ConceptQueryParams().setTextContains("Miligramai per 24 val")).data*.code.isEmpty()
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

  private UcumCodeSystemProvider provider(Map<String, List<Designation>> supplementDesignations = [:]) {
    new UcumCodeSystemProvider(new UcumConceptResolver(new UcumMapper(), ucumService, supplementService(supplementDesignations)))
  }

  private UcumValueSetExpandProvider expandProvider() {
    def supplementService = supplementService([:])
    new UcumValueSetExpandProvider(new UcumConceptResolver(new UcumMapper(), ucumService, supplementService), supplementService)
  }

  private static UcumSupplementDesignationService supplementService(Map<String, List<Designation>> designationsByCode) {
    def codes = designationsByCode.keySet().toList()
    new UcumSupplementDesignationService(
        new CodeSystemRepository() {
          @Override
          QueryResult<CodeSystem> query(CodeSystemQueryParams params) {
            if (params.content == "supplement") {
              return new QueryResult([new CodeSystem().setId("ucum-supplement-lt").setBaseCodeSystem("ucum")])
            }
            if (params.uri == "https://termx.org/fhir/CodeSystem/ucum-supplement-lt") {
              return new QueryResult([new CodeSystem().setId("ucum-supplement-lt").setBaseCodeSystem("ucum")])
            }
            return QueryResult.empty()
          }

          @Override
          CodeSystem load(String codeSystem) {
            return "ucum-supplement-lt" == codeSystem ? new CodeSystem().setId("ucum-supplement-lt").setBaseCodeSystem("ucum") : null
          }
        },
        new CodeSystemVersionRepository() {
          @Override
          QueryResult<CodeSystemVersion> query(CodeSystemVersionQueryParams params) {
            return new QueryResult([new CodeSystemVersion().setVersion("1.0.0").setStatus("active")])
          }
        },
        new ConceptRepository() {
          @Override
          QueryResult<Concept> query(ConceptQueryParams params) {
            def matchedCodes = codes.findAll { params.codes == null || params.codes.contains(it) }
            return new QueryResult(matchedCodes.withIndex().collect { entry ->
              new Concept().setId(entry.v2 + 1L).setCode(entry.v1).setCodeSystem(params.codeSystem)
            })
          }
        },
        new CodeSystemEntityVersionRepository() {
          @Override
          QueryResult<CodeSystemEntityVersion> query(CodeSystemEntityVersionQueryParams params) {
            return new QueryResult(codes.withIndex().collect { entry ->
              new CodeSystemEntityVersion().setId(entry.v2 + 100L).setCode(entry.v1).setCodeSystem(params.codeSystem)
            })
          }
        },
        new DesignationService(null) {
          @Override
          QueryResult<Designation> query(DesignationQueryParams params) {
            def ids = params.codeSystemEntityVersionId?.split(",")?.findAll()?.collect { it as Long } ?: []
            def data = ids.collectMany { id ->
              int idx = (id - 100L) as int
              if (idx < 0 || idx >= codes.size()) {
                return []
              }
              return (designationsByCode[codes[idx]] ?: []).collect { designation ->
                new Designation()
                    .setCodeSystemEntityVersionId(id)
                    .setDesignationType(designation.designationType)
                    .setLanguage(designation.language)
                    .setName(designation.name)
                    .setPreferred(designation.preferred)
                    .setSupplement(designation.supplement)
              }
            }
            return new QueryResult(data)
          }
        }
    )
  }

  private UcumSupplementDesignationService supplementServiceForCachedUnits(List<UcumUnitDefinition> cachedUnits) {
    new UcumSupplementDesignationService(
        new CodeSystemRepository() {
          @Override
          QueryResult<CodeSystem> query(CodeSystemQueryParams params) {
            return QueryResult.empty()
          }

          @Override
          CodeSystem load(String codeSystem) {
            return null
          }
        },
        new CodeSystemVersionRepository() {
        },
        new ConceptRepository() {
        },
        new CodeSystemEntityVersionRepository() {
        },
        new DesignationService(null) {
        }
    ) {
      @Override
      List<UcumUnitDefinition> loadSupplementUnitDefinitions() {
        return cachedUnits.collect { unit ->
          new UcumUnitDefinition()
              .setCode(unit.code)
              .setKind(unit.kind)
              .setProperty(unit.property)
              .setNames(unit.names == null ? null : new ArrayList<>(unit.names))
        }
      }
    }
  }

  private static ValidateResponseDto valid(boolean isValid) {
    def dto = new ValidateResponseDto()
    dto.setValid(isValid)
    return dto
  }

  private static Designation designation(String type, String language, String value, boolean preferred, boolean supplement) {
    new Designation()
        .setDesignationType(type)
        .setLanguage(language)
        .setName(value)
        .setPreferred(preferred)
        .setSupplement(supplement)
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
