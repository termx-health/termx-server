package com.kodality.termserver.ts.measurementunit;

import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.auth.auth.Authorized;
import com.kodality.termserver.measurementunit.MeasurementUnit;
import com.kodality.termserver.measurementunit.MeasurementUnitQueryParams;
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

  @Authorized("*.MeasurementUnit.view")
  @Get("{?params*}")
  public QueryResult<MeasurementUnit> query(MeasurementUnitQueryParams params) {
    return measurementUnitService.query(params);
  }

  @Authorized("*.MeasurementUnit.view")
  @Get("/kinds")
  public List<String> loadKinds() {
    return measurementUnitService.loadKinds();
  }

  @Authorized("*.MeasurementUnit.view")
  @Get("/{id}")
  public MeasurementUnit load(@Parameter Long id) {
    return measurementUnitService.load(id);
  }

  @Authorized("*.MeasurementUnit.edit")
  @Post
  public MeasurementUnit save(@Valid @Body MeasurementUnit unit) {
    unit.setId(null);
    measurementUnitService.save(unit);
    return unit;
  }

  @Authorized("*.MeasurementUnit.edit")
  @Put("/{id}")
  public MeasurementUnit update(@Parameter Long id, @Valid @Body MeasurementUnit unit) {
    unit.setId(id);
    measurementUnitService.save(unit);
    return unit;
  }

  @Authorized("*.MeasurementUnit.view")
  @Get("convert")
  public Map<String, Object> convert(@QueryValue BigDecimal value, @QueryValue String from, @QueryValue String to) {
    BigDecimal converted = measurementUnitConverter.convert(value, from, to);
    return Map.of("value", new BigDecimal(converted.toPlainString()), "unit", to);
  }
}
