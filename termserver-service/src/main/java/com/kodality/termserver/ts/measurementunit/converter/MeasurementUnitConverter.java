package com.kodality.termserver.ts.measurementunit.converter;

import com.kodality.termserver.ApiError;
import com.kodality.termserver.measurementunit.MeasurementUnit;
import com.kodality.termserver.ts.measurementunit.MeasurementUnitService;
import java.math.BigDecimal;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Singleton
public class MeasurementUnitConverter {
  private final MeasurementUnitService measurementUnitService;

  public BigDecimal convert(BigDecimal value, String fromCode, String toCode) {
    if (value == null) {
      return null;
    }
    return convert(value, measurementUnitService.load(fromCode), measurementUnitService.load(toCode));
  }

  public BigDecimal convert(BigDecimal value, MeasurementUnit from, MeasurementUnit to) {
    if (value == null) {
      return null;
    }
    if (!from.getKind().equals(to.getKind())) {
      throw ApiError.TE601.toApiException();
    }
    return doConvert(value, from, to);
  }


  private BigDecimal doConvert(BigDecimal value, MeasurementUnit from, MeasurementUnit to) {
    if (to.getCode().equals(from.getCode())) {
      return value;
    }

    if (from.getDefinitionUnit() != null) {
      MeasurementUnit baseFrom = measurementUnitService.load(from.getDefinitionUnit());
      return doConvert(value.multiply(parseCoef(from.getDefinitionValue())), baseFrom, to);
    }

    if (to.getDefinitionUnit() == null) {
      throw ApiError.TE602.toApiException();
    }

    MeasurementUnit baseTo = measurementUnitService.load(to.getDefinitionUnit());
    return doConvert(value, from, baseTo).divide(parseCoef(to.getDefinitionValue()));
  }

  private BigDecimal parseCoef(String coef) {
    return new BigDecimal(coef);
  }

}
