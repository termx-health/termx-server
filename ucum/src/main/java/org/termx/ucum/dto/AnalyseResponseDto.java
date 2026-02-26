package org.termx.ucum.dto;

import io.micronaut.core.annotation.Introspected;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Introspected
@Schema(
  name = "AnalyseResponse",
  description = "Response containing a UCUM code converted into a human-readable expression"
)
public class AnalyseResponseDto {
    @Schema(
      description = "Human-readable form of the UCUM expression",
      example = "(gram) * (meter ^ -6) * (second ^ -1)"
    )
    @Getter @Setter
    private String result;
}
