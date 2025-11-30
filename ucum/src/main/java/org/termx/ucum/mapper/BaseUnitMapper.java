package org.termx.ucum.mapper;

import org.fhir.ucum.BaseUnit;
import org.springframework.stereotype.Component;
import org.termx.ucum.dto.BaseUnitDto;

@Component
public class BaseUnitMapper {
    public BaseUnitDto toDto(BaseUnit baseUnit) {
        BaseUnitDto dto = new BaseUnitDto();
        dto.setCode(baseUnit.getCode());
        dto.setCodeUC(baseUnit.getCodeUC());
        dto.setPrintSymbol(baseUnit.getPrintSymbol());
        dto.setNames(baseUnit.getNames());
        dto.setProperty(baseUnit.getProperty());
        dto.setDimension(String.valueOf(baseUnit.getDim()));
        dto.setDescription(baseUnit.getDescription());

        return dto;
    }
}
