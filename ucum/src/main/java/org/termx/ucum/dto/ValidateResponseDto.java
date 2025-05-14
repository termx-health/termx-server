package org.termx.ucum.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.core.annotation.Introspected;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Introspected
@Schema(
  name = "ValidateResponse",
  description = "Response indicating whether the UCUM code is valid and an optional error message"
)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ValidateResponseDto {
    @Schema(
      description = "Indicates whether the UCUM code passed validation",
      example = "true"
    )
    @Getter @Setter
    private boolean valid;

    @Schema(
      description = "Error message if the UCUM code is invalid",
      example = "Invalid UCUM code",
      nullable = true
    )
    @Getter @Setter
    private String errorMessage;
}
