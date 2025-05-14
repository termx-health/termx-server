package org.termx.ucum.mapper;

import org.fhir.ucum.UcumService.UcumVersionDetails;
import org.springframework.stereotype.Component;
import org.termx.ucum.dto.UcumVersionDto;

@Component
public class UcumVersionMapper {
    public UcumVersionDto toDto(UcumVersionDetails versionDetails) {
        UcumVersionDto dto = new UcumVersionDto();
        dto.setVersion(versionDetails.getVersion());
        dto.setReleaseDate(versionDetails.getReleaseDate().toString());

        return dto;
    }
}
