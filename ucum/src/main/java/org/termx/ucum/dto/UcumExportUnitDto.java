package org.termx.ucum.dto;

import io.micronaut.core.annotation.Introspected;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Introspected
@Schema(name = "UcumExportUnit", description = "A UCUM unit with merged base and supplement designations")
public class UcumExportUnitDto {
  @Schema(description = "UCUM code", example = "mL")
  private String code;

  @Schema(description = "UCUM kind", example = "UNIT")
  private String kind;

  @Schema(description = "UCUM property", example = "volume")
  private String property;

  @Schema(description = "Merged designations")
  private List<UcumExportDesignationDto> designations;
}
