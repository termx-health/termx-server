package org.termx.ucum.dto;

import io.micronaut.core.annotation.Introspected;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Introspected
@Schema(name = "UcumVersion", description = "A UCUM version")
public class UcumVersionDto {

    @Schema(description = "The version of the UCUM specification", example = "2.2")
    @Getter @Setter
    private String version;

    @Schema(description = "The release date of the UCUM specification", example = "2024-06-16T21:00:00.000+00:00")
    @Getter @Setter
    private String releaseDate;
}
