package com.kodality.termserver.fhir;

import com.kodality.termserver.auth.Authorized;
import com.kodality.zmei.fhir.resource.other.Parameters;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.QueryValue;
import java.math.BigDecimal;
import javax.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller("/fhir/CodeSystem")
@RequiredArgsConstructor
public class UcumFhirController {
  private final FhirMeasurementUnitConvertor fhirMeasurementUnitConvertor;

  @Authorized("*.MeasurementUnit.view")
  @Get("/ucum/$translate")
  public HttpResponse<Parameters> translate(@QueryValue @NotNull BigDecimal value, @QueryValue @NotNull String sourceUnit, @QueryValue @NotNull String targetUnit) {
    return HttpResponse.ok(fhirMeasurementUnitConvertor.convert(value, sourceUnit, targetUnit));
  }
}
