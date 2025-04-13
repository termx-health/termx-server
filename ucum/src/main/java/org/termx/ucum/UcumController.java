package org.termx.ucum;

import com.kodality.termx.core.auth.Authorized;
import com.kodality.termx.ucum.Privilege;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.validation.Validated;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.fhir.ucum.Decimal;

import java.util.Map;

@Slf4j
@Validated
@RequiredArgsConstructor
@Controller("/ucum")
public class UcumController {
    private final UcumServiceImpl ucumService;

    @Authorized(Privilege.UCUM_VIEW)
    @Get("/validate")
    public Map<String, Boolean> validateUnit(@QueryValue String unit) {
        boolean isValid = ucumService.isUnitValid(unit);
        return Map.of("valid", isValid);
    }

    @Authorized(Privilege.UCUM_VIEW)
    @Get("/analyse")
    public String analyseUcumCode(@QueryValue String ucumCode) {
        try {
            return ucumService.analyse(ucumCode);
        } catch (Exception e) {
            return e.getMessage();
        }
    }

    @Authorized(Privilege.UCUM_VIEW)
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
                    "targetUnit", targetUnit
            );
        } catch (Exception e) {
            return Map.of(
                    "Error: ", e.getMessage()
            );
        }
    }
}
