package org.termx.ucum.mapper;

import org.fhir.ucum.Decimal;
import org.springframework.stereotype.Component;
import org.termx.ucum.dto.ConvertResponseDto;

@Component
public class ConvertResponseMapper {
    public ConvertResponseDto toDto(Decimal conversionResult) {
        ConvertResponseDto dto = new ConvertResponseDto();
        dto.setResult(conversionResult.asDecimal());
        return dto;
    }
}
