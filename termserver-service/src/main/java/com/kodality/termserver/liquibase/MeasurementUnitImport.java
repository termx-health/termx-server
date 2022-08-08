package com.kodality.termserver.liquibase;

import com.kodality.commons.micronaut.BeanContext;
import com.kodality.commons.util.JsonUtil;
import com.kodality.termserver.common.AuthorizedFileReaderCustomChange;
import com.kodality.termserver.measurementunit.MeasurementUnit;
import com.kodality.termserver.ts.measurementunit.MeasurementUnitService;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MeasurementUnitImport extends AuthorizedFileReaderCustomChange {
  private final MeasurementUnitService measurementUnitService;

  public MeasurementUnitImport() {
    measurementUnitService = BeanContext.getBean(MeasurementUnitService.class);
  }

  @Override
  protected void handleMigrationFile(String name, byte[] content) {
    log.info("Updating measurement units from " + name);
    List<MeasurementUnit> measurementUnits = JsonUtil.fromJson(asString(content), JsonUtil.getListType(MeasurementUnit.class));
    measurementUnits.forEach(measurementUnitService::merge);
  }
}
