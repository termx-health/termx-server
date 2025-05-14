package org.termx.ucum.mapper;

import org.fhir.ucum.DefinedUnit;
import org.springframework.stereotype.Component;
import org.termx.ucum.dto.DefinedUnitDto;

@Component
public class DefinedUnitMapper {
    public DefinedUnitDto toDto(DefinedUnit definedUnit) {
        DefinedUnitDto dto = new DefinedUnitDto();
        dto.setCode(definedUnit.getCode());
        dto.setCodeUC(definedUnit.getCodeUC());
        dto.setPrintSymbol(definedUnit.getPrintSymbol());
        dto.setNames(definedUnit.getNames());
        dto.setProperty(definedUnit.getProperty());
        dto.setMetric(definedUnit.isMetric());
        dto.setClass_(definedUnit.getClass_());
        dto.setDescription(definedUnit.getDescription());
        dto.setSpecial(definedUnit.isSpecial());

        return dto;
    }
}
