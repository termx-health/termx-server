package org.termx.ucum.mapper;

import org.springframework.stereotype.Component;
import org.termx.ucum.dto.ValidateResponseDto;

@Component
public class ValidateResponseMapper {
    public ValidateResponseDto toDto(String errorMessage) {
        ValidateResponseDto dto = new ValidateResponseDto();
        dto.setValid(errorMessage == null);
        if (errorMessage != null) {
            dto.setErrorMessage(errorMessage);
        }
        return dto;
    }
}
