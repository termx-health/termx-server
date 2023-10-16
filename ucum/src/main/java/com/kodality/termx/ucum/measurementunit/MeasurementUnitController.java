package com.kodality.termx.ucum.measurementunit;


import com.kodality.commons.model.QueryResult;
import com.kodality.termx.ucum.Privilege;
import com.kodality.termx.core.auth.Authorized;
import com.kodality.termx.ucum.measurementunit.converter.MeasurementUnitConverter;
import com.kodality.termx.ucum.MeasurementUnit;
import com.kodality.termx.ucum.MeasurementUnitQueryParams;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
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

  @Authorized(Privilege.UCUM_VIEW)
  @Get("{?params*}")
  public QueryResult<MeasurementUnit> query(MeasurementUnitQueryParams params) {
    return measurementUnitService.query(params);
  }

  @Authorized(Privilege.UCUM_VIEW)
  @Get("/kinds")
  public List<String> loadKinds() {
    return measurementUnitService.loadKinds();
  }

  @Authorized(Privilege.UCUM_VIEW)
  @Get("/{id}")
  public MeasurementUnit load(@PathVariable Long id) {
    return measurementUnitService.load(id);
  }

  @Authorized(Privilege.UCUM_EDIT)
  @Post
  public MeasurementUnit save(@Valid @Body MeasurementUnit unit) {
    unit.setId(null);
    measurementUnitService.save(unit);
    return unit;
  }

  @Authorized(Privilege.UCUM_EDIT)
  @Put("/{id}")
  public MeasurementUnit update(@PathVariable Long id, @Valid @Body MeasurementUnit unit) {
    unit.setId(id);
    measurementUnitService.save(unit);
    return unit;
  }

  @Authorized(Privilege.UCUM_VIEW)
  @Get("convert")
  public Map<String, Object> convert(@QueryValue BigDecimal value, @QueryValue String from, @QueryValue String to) {
    BigDecimal converted = measurementUnitConverter.convert(value, from, to);
    return Map.of("value", new BigDecimal(converted.toPlainString()), "unit", to);
  }
}
