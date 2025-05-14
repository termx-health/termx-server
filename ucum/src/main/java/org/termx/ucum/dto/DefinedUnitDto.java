package org.termx.ucum.dto;

import io.micronaut.core.annotation.Introspected;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.AccessMode;
import lombok.Getter;
import lombok.Setter;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@Introspected
@Schema(name = "DefinedUnit", description = "A UCUM defined unit")
public class DefinedUnitDto {
    @Schema(
      description = "The kind of concept (always 'UNIT')",
      example = "UNIT",
      accessMode = AccessMode.READ_ONLY
    )
    @Getter
    private final String kind = "UNIT";

    @Schema(description = "The UCUM code", example = "l")
    @Getter @Setter
    private String code;

    @Schema(description = "The UCUM code (uppercase)", example = "L")
    @Getter @Setter
    private String codeUC;

    @Schema(description = "Printable symbol for the unit", example = "l")
    @Getter @Setter
    private String printSymbol;

    @Schema(description = "Unit names", example = "[\"liter\"]")
    @Getter @Setter
    private List<String> names;

    @Schema(description = "Property of the unit", example = "volume")
    @Getter @Setter
    private String property;

    @Schema(description = "Metric flag", example = "true", type = "boolean")
    @Getter @Setter
    private Boolean metric;

    @Schema(description = "Classification of the unit", example = "iso1000")
    @JsonProperty("class")
    @Getter @Setter
    private String class_;

    @Schema(description = "Human-readable description", example = "unit l ('liter') (volume) = 1dm3")
    @Getter @Setter
    private String description;

    @Schema(description = "Flags whether this unit is a special UCUM unit requiring custom conversion functions (non-ratio scale)",
            example = "false", type = "boolean")
    @Getter @Setter
    private Boolean special;
}