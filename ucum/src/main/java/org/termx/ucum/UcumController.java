package org.termx.ucum;

import com.kodality.termx.core.auth.Authorized;
import com.kodality.termx.ucum.Privilege;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.validation.Validated;
import lombok.RequiredArgsConstructor;
import org.fhir.ucum.Decimal;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

@Produces(MediaType.APPLICATION_JSON)
@Validated
@RequiredArgsConstructor
@Controller("/ucum")
public class UcumController {
    private final UcumService ucumService;

    @Authorized(Privilege.UCUM_VIEW)
    @Get("/validate")
    public HttpResponse<Map<String, Boolean>> validateUnit(@QueryValue @NotBlank String unit) {
        boolean isValid = ucumService.isUnitValid(unit);
        return HttpResponse.ok(Map.of("valid", isValid));
    }

    @Authorized(Privilege.UCUM_VIEW)
    @Get("/analyse")
    public HttpResponse<String> analyseUcumCode(@QueryValue @NotBlank String unit) {
        try {
            String result = ucumService.analyse(unit);
            return HttpResponse.ok(result);
        } catch (Exception e) {
            return HttpResponse.badRequest(e.getMessage());
        }
    }

    @Authorized(Privilege.UCUM_VIEW)
    @Get("/convert")
    public HttpResponse<Map<String, Object>> convert(@QueryValue @NotBlank String value,
                                                     @QueryValue @NotBlank String sourceUnit,
                                                     @QueryValue @NotBlank String targetUnit) {
        try {
            Decimal decimalValue = new Decimal(value);
            Decimal result = ucumService.convert(decimalValue, sourceUnit, targetUnit);
            return HttpResponse.ok(Map.of(
                    "value", result.toString(),
                    "sourceUnit", sourceUnit,
                    "targetUnit", targetUnit
            ));
        } catch (Exception e) {
            return HttpResponse.badRequest(Map.of("error", e.getMessage()));
        }
    }

    @Authorized(Privilege.UCUM_VIEW)
    @Get("/version")
    public HttpResponse<Map<String, Object>> getUcumVersionDetails() {
        try {
            Object result = ucumService.getUcumVersionDetails();
            return HttpResponse.ok(Map.of("ucumVersionDetails", result));
        } catch (Exception e) {
            return HttpResponse.badRequest(Map.of("error", e.getMessage()));
        }
    }

    @Authorized(Privilege.UCUM_VIEW)
    @Get("/canonical")
    public HttpResponse<Map<String, Object>> getCanonicalUnits(@QueryValue @NotBlank String unit) {
        try {
            String result = ucumService.getCanonicalUnits(unit);
            return HttpResponse.ok(Map.of("canonicalUnits", result));
        } catch (Exception e) {
            return HttpResponse.badRequest(Map.of("error", e.getMessage()));
        }
    }

    @Authorized(Privilege.UCUM_VIEW)
    @Get("/components")
    public HttpResponse<Map<String, Object>> getUcumComponents() {
        try {
            Object result = ucumService.getUcumComponents();
            return HttpResponse.ok(Map.of("ucumComponents", result));
        } catch (Exception e) {
            return HttpResponse.badRequest(Map.of("error", e.getMessage()));
        }
    }

    @Authorized(Privilege.UCUM_VIEW)
    @Get("/search")
    public HttpResponse<Map<String, Object>> searchComponent(@QueryValue @NotBlank String text) {
        try {
            Object result = ucumService.searchComponent(text);
            return HttpResponse.ok(Map.of("ucumComponentProperties", result));
        } catch (Exception e) {
            return HttpResponse.badRequest(Map.of("error", e.getMessage()));
        }
    }
}
