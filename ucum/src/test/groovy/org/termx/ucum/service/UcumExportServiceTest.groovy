package org.termx.ucum.service

import com.kodality.commons.model.QueryResult
import com.kodality.termx.terminology.terminology.codesystem.CodeSystemService
import com.kodality.termx.terminology.terminology.codesystem.concept.ConceptService
import com.kodality.termx.ts.codesystem.CodeSystem
import com.kodality.termx.ts.codesystem.CodeSystemEntityVersion
import com.kodality.termx.ts.codesystem.Concept
import com.kodality.termx.ts.codesystem.CodeSystemQueryParams
import com.kodality.termx.ts.codesystem.ConceptQueryParams
import com.kodality.termx.ts.codesystem.Designation
import com.kodality.termx.ucum.ts.UcumMapper
import com.kodality.termx.ucum.ts.UcumConceptResolver
import org.termx.ucum.dto.DefinedUnitDto
import org.termx.ucum.dto.UcumExportRequestDto
import org.termx.ucum.dto.ValidateResponseDto
import spock.lang.Specification

class UcumExportServiceTest extends Specification {
  def "export merges base ucum designations with supplement designations and includes supplement-only codes"() {
    given:
    def ucumService = Stub(UcumService) {
      getBaseUnits() >> []
      getDefinedUnits() >> [
          unit("mL", ["milliliter"], "volume"),
      ]
      validate(_ as String) >> { args ->
        def dto = new ValidateResponseDto()
        dto.setValid(args[0] in ["mL", "mg/(24.h)"])
        return dto
      }
    }
    def resolver = new UcumConceptResolver(new UcumMapper(), ucumService)
    def codeSystemService = new CodeSystemService(null, null, null, null, null) {
      @Override
      QueryResult<CodeSystem> query(CodeSystemQueryParams params) {
        return new QueryResult([
            new CodeSystem().setId("ucum-supplement-lt").setUri("https://termx.org/fhir/CodeSystem/ucum-supplement-lt").setBaseCodeSystem("ucum").setVersions([
                new com.kodality.termx.ts.codesystem.CodeSystemVersion().setVersion("1.0.0").setStatus("active")
            ])
        ])
      }
    }
    def conceptService = new ConceptService(null, null, null, null, null, List.of()) {
      @Override
      QueryResult<Concept> query(ConceptQueryParams params) {
        assert params.codeSystem == "ucum-supplement-lt"
        return new QueryResult([
            new Concept().setCode("mL").setVersions([
                new CodeSystemEntityVersion().setDesignations([designation("display", "lt", "Mililitras", false, true)])
            ]),
            new Concept().setCode("mg/(24.h)").setVersions([
                new CodeSystemEntityVersion().setDesignations([designation("display", "lt", "Miligramai per 24 val", false, true)])
            ])
        ])
      }
    }

    def service = new UcumExportService(resolver, codeSystemService, conceptService)
    def request = new UcumExportRequestDto()
    request.setSupplements(["ucum-supplement-lt"])

    when:
    def response = service.export(request)

    then:
    response.supplements == ["https://termx.org/fhir/CodeSystem/ucum-supplement-lt"]
    response.units*.code == ["mL", "mg/(24.h)"]
    response.units.find { it.code == "mL" }.designations*.value.containsAll(["milliliter", "Mililitras"])
    response.units.find { it.code == "mg/(24.h)" }.designations*.value.containsAll(["mg/(24.h)", "Miligramai per 24 val"])
  }

  private static Designation designation(String type, String language, String value, boolean preferred, boolean supplement) {
    new Designation()
        .setDesignationType(type)
        .setLanguage(language)
        .setName(value)
        .setPreferred(preferred)
        .setSupplement(supplement)
  }

  private static DefinedUnitDto unit(String code, List<String> names, String property) {
    def dto = new DefinedUnitDto()
    dto.setCode(code)
    dto.setNames(names)
    dto.setProperty(property)
    return dto
  }
}
