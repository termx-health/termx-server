package org.termx.ucum.mapper;

import org.fhir.ucum.Prefix;
import org.springframework.stereotype.Component;
import org.termx.ucum.dto.PrefixDto;

@Component
public class PrefixMapper {
    public PrefixDto toDto(Prefix prefix) {
        PrefixDto dto = new PrefixDto();
        dto.setCode(prefix.getCode());
        dto.setCodeUC(prefix.getCodeUC());
        dto.setPrintSymbol(prefix.getPrintSymbol());
        dto.setNames(prefix.getNames());
        dto.setDescription(prefix.getDescription());

        return dto;
    }
}
