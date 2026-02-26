package org.termx.ucum.controller;

import com.kodality.termx.core.auth.Authorized;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.version.annotation.Version;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.annotation.Error;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.validation.Validated;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.fhir.ucum.*;
import org.termx.ucum.dto.*;
import org.termx.ucum.exception.InvalidUcumRequestException;
import org.termx.ucum.service.UcumService;
import org.termx.ucum.security.Privilege;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;

@Validated
@RequiredArgsConstructor
@Version("1")
@Controller("/api/v1/ucum")
@Tag(name = "UCUM", description = "UCUM service operations")
@Produces(MediaType.APPLICATION_JSON)
public class UcumController {
    private final UcumService ucumService;

    @Operation(summary = "Get UCUM version details")
    @ApiResponse(
      responseCode = "200",
      description = "UCUM version information",
      content = @Content(
        schema = @Schema(implementation = UcumVersionDto.class)
      )
    )
    @Authorized(Privilege.UCUM_VIEW)
    @Get("/version")
    public HttpResponse<UcumVersionDto> getUcumVersionDetails() {
        UcumVersionDto response = ucumService.getUcumVersionDetails();
        return HttpResponse.ok(response);
    }

    @Operation(summary = "Validate a UCUM code")
    @ApiResponse(
        responseCode = "200",
        description = "Validation response",
        content = @Content(
          schema = @Schema(implementation = ValidateResponseDto.class)
        )
    )
    @Authorized(Privilege.UCUM_VIEW)
    @Get("/validate")
    public HttpResponse<ValidateResponseDto> validate(
            @QueryValue(defaultValue = "") @NotBlank String code) {
        ValidateResponseDto response = ucumService.validate(code);
        return HttpResponse.ok(response);
    }

    @Operation(summary = "Analyze a UCUM code")
    @ApiResponses({
      @ApiResponse(
        responseCode = "200",
        description = "Analysis response",
        content = @Content(
          schema = @Schema(implementation = AnalyseResponseDto.class)
        )
      ),
      @ApiResponse(
        responseCode = "400",
        description = "UCUM code validation failed",
        content = @Content(
          schema = @Schema(implementation = ErrorDto.class)
        )
      )
    })
    @Authorized(Privilege.UCUM_VIEW)
    @Get("/analyse")
    public HttpResponse<AnalyseResponseDto> analyse(
            @QueryValue(defaultValue = "") @NotBlank String code) throws UcumException {
        try {
            AnalyseResponseDto response = ucumService.analyse(code);
            return HttpResponse.ok(response);
        } catch (InvalidUcumRequestException e) {
            throw new HttpStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @Operation(summary = "Convert a value between units")
    @ApiResponses({
      @ApiResponse(
        responseCode = "200",
        description = "Conversion response",
        content = @Content(
          schema = @Schema(implementation = ConvertResponseDto.class)
        )
      ),
      @ApiResponse(
        responseCode = "400",
        description = "UCUM source or target code validation failed",
        content = @Content(
          schema = @Schema(implementation = ErrorDto.class)
        )
      )
    })
    @Authorized(Privilege.UCUM_VIEW)
    @Get("/convert")
    public HttpResponse<ConvertResponseDto> convert(
            @Nullable @NotNull @QueryValue BigDecimal value,
            @QueryValue(defaultValue = "") @NotBlank String sourceCode,
            @QueryValue(defaultValue = "") @NotBlank String targetCode) throws UcumException {
        try {
            ConvertResponseDto response = ucumService.convert(value, sourceCode, targetCode);
            return HttpResponse.ok(response);
        } catch (InvalidUcumRequestException e) {
            throw new HttpStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @Operation(summary = "Get a UCUM code converted into its canonical form")
    @ApiResponses({
      @ApiResponse(
        responseCode = "200",
        description = "Canonical units response",
        content = @Content(
          schema = @Schema(implementation = CanonicaliseResponseDto.class)
        )
      ),
      @ApiResponse(
        responseCode = "400",
        description = "UCUM code validation failed",
        content = @Content(
          schema = @Schema(implementation = ErrorDto.class)
        )
      )
    })
    @Authorized(Privilege.UCUM_VIEW)
    @Get("/canonicalise")
    public HttpResponse<CanonicaliseResponseDto> getCanonicalUnits(
            @QueryValue(defaultValue = "") @NotBlank String code) throws UcumException {

        try {
            CanonicaliseResponseDto response = ucumService.getCanonicalUnits(code);
            return HttpResponse.ok(response);
        } catch (InvalidUcumRequestException e) {
            throw new HttpStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @Operation(summary = "List all defined units")
    @ApiResponse(
      responseCode = "200",
      description = "List of defined units",
      content = @Content(
        schema = @Schema(implementation = DefinedUnitDto.class, type = "array")
      )
    )
    @Authorized(Privilege.UCUM_VIEW)
    @Get("/defined-units")
    public HttpResponse<List<DefinedUnitDto>> getDefinedUnits() {
        List<DefinedUnitDto> response = ucumService.getDefinedUnits();
        return HttpResponse.ok(response);
    }

    @Operation(summary = "Get a defined unit by code")
    @ApiResponses({
      @ApiResponse(
        responseCode = "200",
        description = "A defined unit",
        content = @Content(
          schema = @Schema(implementation = DefinedUnitDto.class)
        )
      ),
      @ApiResponse(
        responseCode = "404",
        description = "Defined unit not found",
        content = @Content(
          schema = @Schema(implementation = ErrorDto.class)
        )
      )
    })
    @Authorized(Privilege.UCUM_VIEW)
    @Get("/defined-units/{code}")
    public HttpResponse<DefinedUnitDto> getDefinedUnitByCode(@PathVariable String code) {
        DefinedUnitDto response = ucumService.getDefinedUnitByCode(code);
        if (response == null) {
            throw new HttpStatusException(
                    HttpStatus.NOT_FOUND,
                    String.format("Defined unit with code '%s' was not found", code)
            );
        }
        return HttpResponse.ok(response);
    }

    @Operation(summary = "List all base units")
    @ApiResponse(
      responseCode = "200",
      description = "List of base units",
      content = @Content(
        schema = @Schema(implementation = BaseUnitDto.class, type = "array")
      )
    )
    @Authorized(Privilege.UCUM_VIEW)
    @Get("/base-units")
    public HttpResponse<List<BaseUnitDto>> getBaseUnits() {
        List<BaseUnitDto> response = ucumService.getBaseUnits();
        return HttpResponse.ok(response);
    }

    @Operation(summary = "Get a base unit by code")
    @ApiResponses({
      @ApiResponse(
        responseCode = "200",
        description = "A base unit",
        content = @Content(
          schema = @Schema(implementation = BaseUnitDto.class)
        )
      ),
      @ApiResponse(
        responseCode = "404",
        description = "Base unit not found",
        content = @Content(
          schema = @Schema(implementation = ErrorDto.class)
        )
      )
    })
    @Authorized(Privilege.UCUM_VIEW)
    @Get("/base-units/{code}")
    public HttpResponse<BaseUnitDto> getBaseUnitByCode(@PathVariable String code) {
        BaseUnitDto response = ucumService.getBaseUnitByCode(code);
        if (response == null) {
            throw new HttpStatusException(
                    HttpStatus.NOT_FOUND,
                    String.format("Defined unit with code '%s' was not found", code)
            );
        }
        return HttpResponse.ok(response);
    }

    @Operation(summary = "List all prefixes")
    @ApiResponse(
      responseCode = "200",
      description = "List of prefixes",
      content = @Content(
        schema = @Schema(implementation = PrefixDto.class, type = "array")
      )
    )
    @Authorized(Privilege.UCUM_VIEW)
    @Get("/prefixes")
    public HttpResponse<List<PrefixDto>> getPrefixes() {
        List<PrefixDto> response = ucumService.getPrefixes();
        return HttpResponse.ok(response);
    }

    @Operation(summary = "Get a prefix by code")
    @ApiResponses({
      @ApiResponse(
        responseCode = "200",
        description = "A prefix",
        content = @Content(
          schema = @Schema(implementation = PrefixDto.class)
        )
      ),
      @ApiResponse(
        responseCode = "404",
        description = "Prefix not found",
        content = @Content(
          schema = @Schema(implementation = ErrorDto.class)
        )
      )
    })
    @Authorized(Privilege.UCUM_VIEW)
    @Get("/prefixes/{code}")
    public HttpResponse<PrefixDto> getPrefixByCode(@PathVariable String code) {
        PrefixDto response = ucumService.getPrefixByCode(code);
        if (response == null) {
            throw new HttpStatusException(
                    HttpStatus.NOT_FOUND,
                    String.format("Prefix with code '%s' was not found", code)
            );
        }
        return HttpResponse.ok(response);
    }

    @Error(exception = HttpStatusException.class)
    public HttpResponse<ErrorDto> onStatusException(HttpRequest<?> request, HttpStatusException e) {
        ErrorDto error = new ErrorDto(e.getStatus(), e.getMessage(), request.getPath());
        return HttpResponse.status(e.getStatus()).body(error);
    }
}
