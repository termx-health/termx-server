package org.termx.ucum.dto;

import io.micronaut.core.annotation.Introspected;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;


@Introspected
@Schema(
  name = "ConvertResponse",
  description = "Response containing the converted value for the provided input in the target unit"
)
public class ConvertResponseDto {

    @Schema(
      description = "Converted numeric value resulting from the conversion",
      example = "2500"
    )
    @Getter @Setter
    private String result;
}
