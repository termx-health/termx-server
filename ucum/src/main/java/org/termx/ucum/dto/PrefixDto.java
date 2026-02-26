package org.termx.ucum.dto;

import io.micronaut.core.annotation.Introspected;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Introspected
@Schema(name = "Prefix", description = "A UCUM prefix")
public class PrefixDto {
    @Schema(
        description = "The kind of concept (always 'PREFIX')",
        example = "PREFIX",
        accessMode = Schema.AccessMode.READ_ONLY
    )
    @Getter
    private final String kind = "PREFIX";

    @Schema(description = "The UCUM code", example = "Mi")
    @Getter @Setter
    private String code;

    @Schema(description = "The UCUM code (uppercase)", example = "MIB")
    @Getter @Setter
    private String codeUC;

    @Schema(description = "Printable symbol for the prefix", example = "Mi")
    @Getter @Setter
    private String printSymbol;

    @Schema(description = "Prefix names", example = "[\"mebi\"]")
    @Getter @Setter
    private List<String> names;

    @Schema(description = "Human-readable description", example = "prefix Mi ('mebi') = 1048576")
    @Getter @Setter
    private String description;
}
