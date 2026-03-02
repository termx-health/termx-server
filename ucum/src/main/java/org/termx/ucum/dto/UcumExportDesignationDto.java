package org.termx.ucum.dto;

import io.micronaut.core.annotation.Introspected;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Introspected
@Schema(name = "UcumExportDesignation", description = "A UCUM designation in the export output")
public class UcumExportDesignationDto {
  @Schema(description = "Designation type", example = "display")
  private String type;

  @Schema(description = "Language code", example = "lt")
  private String language;

  @Schema(description = "Designation text", example = "Mililitras")
  private String value;

  @Schema(description = "Indicates whether the designation is preferred", example = "true")
  private boolean preferred;

  @Schema(description = "Indicates whether the designation originated from a supplement", example = "true")
  private boolean supplement;
}
