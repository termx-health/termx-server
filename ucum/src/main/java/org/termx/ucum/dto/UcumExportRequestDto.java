package org.termx.ucum.dto;

import io.micronaut.core.annotation.Introspected;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Introspected
@Schema(name = "UcumExportRequest", description = "Request for exporting UCUM units with supplements")
public class UcumExportRequestDto {
  @Schema(description = "Optional list of UCUM supplement ids or canonical URLs. When omitted, all UCUM supplements are used.")
  private List<String> supplements;
}
