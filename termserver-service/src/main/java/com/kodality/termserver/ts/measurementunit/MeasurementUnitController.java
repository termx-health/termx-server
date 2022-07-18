package com.kodality.termserver.ts.measurementunit;

import com.kodality.termserver.measurementunit.MeasurementUnit;
import com.kodality.termserver.measurementunit.MeasurementUnitSearchParams;
import com.kodality.termserver.ts.measurementunit.converter.MeasurementUnitConverter;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.validation.Validated;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;

@Validated
@RequiredArgsConstructor
@Controller("/ts/measurement-units")
public class MeasurementUnitController {
  private final MeasurementUnitService measurementUnitService;
  private final MeasurementUnitConverter measurementUnitConverter;

  @Get("{?params*}")
  public List<MeasurementUnit> query(MeasurementUnitSearchParams params) {
    return measurementUnitService.query(params);
  }

  @Get("/{id}")
  public MeasurementUnit load(@Parameter Long id) {
    return measurementUnitService.load(id);
  }

  @Post
  public MeasurementUnit save(@Valid @Body MeasurementUnit unit) {
    unit.setId(null);
    measurementUnitService.save(unit);
    return unit;
  }

  @Put("/{id}")
  public MeasurementUnit update(@Parameter Long id, @Valid @Body MeasurementUnit unit) {
    unit.setId(id);
    measurementUnitService.save(unit);
    return unit;
  }

  @Get("convert")
  public Map<String, Object> convert(@QueryValue BigDecimal value, @QueryValue String from, @QueryValue String to) {
    BigDecimal converted = measurementUnitConverter.convert(value, from, to);
    return Map.of("value", new BigDecimal(converted.toPlainString()), "unit", to);
  }
}
