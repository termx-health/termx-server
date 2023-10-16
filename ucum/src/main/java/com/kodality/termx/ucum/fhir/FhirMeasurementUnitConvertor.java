package com.kodality.termx.ucum.fhir;

import com.kodality.commons.exception.ApiException;
import com.kodality.termx.ucum.measurementunit.MeasurementUnitService;
import com.kodality.termx.ucum.measurementunit.converter.MeasurementUnitConverter;
import com.kodality.termx.ucum.MeasurementUnit;
import com.kodality.zmei.fhir.resource.other.Parameters;
import com.kodality.zmei.fhir.resource.other.Parameters.ParametersParameter;
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
      parameters.setParameter(List.of(new ParametersParameter().setName("result").setValueBoolean(false)));
      return parameters;
    }
    MeasurementUnit targetUnit = measurementUnitService.load(target);
    ParametersParameter result = new ParametersParameter().setName("result").setValueBoolean(true);
    int rounding = targetUnit.getRounding() != null ? targetUnit.getRounding().intValue() : 2;
    ParametersParameter factor = new ParametersParameter().setName("factor").setValueDecimal(converted.setScale(rounding, RoundingMode.HALF_UP));
    ParametersParameter scalar = new ParametersParameter().setName("scalar").setValueString(converted.toString());
    parameters.setParameter(List.of(result, factor, scalar));
    return parameters;
  }
}
