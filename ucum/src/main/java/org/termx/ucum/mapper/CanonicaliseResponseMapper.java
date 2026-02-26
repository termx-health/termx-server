package org.termx.ucum.mapper;

import org.springframework.stereotype.Component;
import org.termx.ucum.dto.CanonicaliseResponseDto;

@Component
public class CanonicaliseResponseMapper {
    public CanonicaliseResponseDto toDto(String canonicaliseResult) {
        CanonicaliseResponseDto dto = new CanonicaliseResponseDto();
        dto.setResult(canonicaliseResult);
        return dto;
    }
}
