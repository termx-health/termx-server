package org.termx.ucum.dto;

import io.micronaut.core.annotation.Introspected;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Introspected
@Schema(name = "BaseUnit", description = "A UCUM base unit")
public class BaseUnitDto {
    @Schema(
        description = "The kind of concept (always 'BASEUNIT')",
        example = "BASEUNIT",
        accessMode = Schema.AccessMode.READ_ONLY
    )
    @Getter
    private final String kind = "BASEUNIT";

    @Schema(description = "The UCUM code", example = "g")
    @Getter @Setter
    private String code;

    @Schema(description = "The UCUM code (uppercase)", example = "G")
    @Getter @Setter
    private String codeUC;

    @Schema(description = "Printable symbol for the unit", example = "g")
    @Getter @Setter
    private String printSymbol;

    @Schema(description = "Unit names", example = "[\"gram\"]")
    @Getter @Setter
    private List<String> names;

    @Schema(description = "Property of the unit", example = "mass")
    @Getter @Setter
    private String property;

    @Schema(
      description = "Dimension symbol representing the base physical dimension of the unit (e.g., 'L' for length, 'M' for mass)",
      example = "M"
    )
    @Getter @Setter
    private String dimension;

    @Schema(description = "Human-readable description", example = "baseunit g ('gram') (mass)")
    @Getter @Setter
    private String description;
}
