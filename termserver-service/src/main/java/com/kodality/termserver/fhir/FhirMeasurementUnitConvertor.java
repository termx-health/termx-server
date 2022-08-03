package com.kodality.termserver.fhir;

import com.kodality.commons.exception.ApiException;
import com.kodality.termserver.measurementunit.MeasurementUnit;
import com.kodality.termserver.ts.measurementunit.MeasurementUnitService;
import com.kodality.termserver.ts.measurementunit.converter.MeasurementUnitConverter;
import com.kodality.zmei.fhir.resource.infrastructure.Parameters;
import com.kodality.zmei.fhir.resource.infrastructure.Parameters.Parameter;
import jakarta.inject.Singleton;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class FhirMeasurementUnitConvertor {

  private final MeasurementUnitConverter measurementUnitConverter;
  private final MeasurementUnitService measurementUnitService;

  public Parameters convert(@NotNull BigDecimal value, String source, String target) {
    Parameters parameters = new Parameters();
    BigDecimal converted;
    try {
      converted = measurementUnitConverter.convert(value, source, target);
    } catch (ApiException e) {
      log.error("Failed to convert {} from {} to {}", value, source, target, e);
      parameters.setParameter(List.of(new Parameter().setName("result").setValueBoolean(false)));
      return parameters;
    }
    MeasurementUnit targetUnit = measurementUnitService.load(target);
    Parameter result = new Parameter().setName("result").setValueBoolean(true);
    int rounding = targetUnit.getRounding() != null ? targetUnit.getRounding().intValue() : 2;
    Parameter factor = new Parameter().setName("factor").setValueDecimal(converted.setScale(rounding, RoundingMode.HALF_UP));
    Parameter scalar = new Parameter().setName("scalar").setValueString(converted.toString());
    parameters.setParameter(List.of(result, factor, scalar));
    return parameters;
  }
}
