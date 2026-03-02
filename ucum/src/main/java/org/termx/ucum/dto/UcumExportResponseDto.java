package org.termx.ucum.dto;

import io.micronaut.core.annotation.Introspected;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Introspected
@Schema(name = "UcumExportResponse", description = "UCUM export result with supplements")
public class UcumExportResponseDto {
  @Schema(description = "Supplements used for export")
  private List<String> supplements;

  @Schema(description = "Exported units")
  private List<UcumExportUnitDto> units;
}
