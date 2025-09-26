package org.termx.ucum.dto;

import io.micronaut.core.annotation.Introspected;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Introspected
@Schema(
  name = "CanonicaliseResponse",
  description = "Response containing a UCUM code converted into its base-unit canonical form"
)
public class CanonicaliseResponseDto {
    @Schema(
      description = "The base-unit canonical form of the provided UCUM expression",
      example = "g.m-6.s-1"
    )
    @Getter @Setter
    private String result;
}
