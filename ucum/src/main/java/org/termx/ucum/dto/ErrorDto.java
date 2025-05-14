package org.termx.ucum.dto;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.http.HttpStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Introspected
@Schema(name = "Error", description = "Standard error response containing an HTTP status, a path and an error message")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ErrorDto {
    @Schema(description = "HTTP status of the error", example = "NOT_FOUND")
    private HttpStatus status;

    @Schema(description = "Detailed error message", example = "Defined unit with code 'll' was not found")
    private String message;

    @Schema(description = "Request path that resulted in the error", example = "/api/v1/ucum/defined-units/ll")
    private String path;
}
