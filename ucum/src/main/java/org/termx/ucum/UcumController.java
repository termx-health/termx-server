package org.termx.ucum;

import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.QueryValue;
import lombok.RequiredArgsConstructor;
import org.fhir.ucum.Decimal;

import java.util.Map;

@RequiredArgsConstructor
@Controller("/ucum")
public class UcumController {
    private final UcumServiceImpl ucumService;

    @Get("/validate")
    public Map<String, Boolean> validateUnit(@QueryValue String unit) {
        boolean isValid = ucumService.isValidUnit(unit);
        return Map.of("valid", isValid);
    }

    @Get("/convert")
    public Map<String, Object> convert(@QueryValue String value,
                                       @QueryValue String sourceUnit,
                                       @QueryValue String targetUnit) {
        try {
            Decimal decimalValue = new Decimal(value);
            Decimal result = ucumService.convert(decimalValue, sourceUnit, targetUnit);
            return Map.of(
                    "value", result.toString(),
                    "sourceUnit", sourceUnit,
                    "targetUnit", targetUnit,
                    "success", true
            );
        } catch (Exception e) {
            return Map.of(
                    "success", false,
                    "error", e.getMessage()
            );
        }
    }
}
