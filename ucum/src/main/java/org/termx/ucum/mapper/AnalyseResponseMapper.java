package org.termx.ucum.mapper;

import org.springframework.stereotype.Component;
import org.termx.ucum.dto.AnalyseResponseDto;

@Component
public class AnalyseResponseMapper {
    public AnalyseResponseDto toDto(String analysisResult) {
        AnalyseResponseDto dto = new AnalyseResponseDto();
        dto.setResult(analysisResult);
        return dto;
    }
}
