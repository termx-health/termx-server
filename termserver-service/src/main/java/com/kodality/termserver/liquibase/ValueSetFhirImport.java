package com.kodality.termserver.liquibase;

import com.kodality.commons.micronaut.BeanContext;
import com.kodality.commons.util.JsonUtil;
import com.kodality.termserver.AuthorizedFileReaderCustomChange;
import com.kodality.termserver.fhir.valueset.ValueSetFhirImportService;
import com.kodality.zmei.fhir.resource.terminology.ValueSet;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ValueSetFhirImport extends AuthorizedFileReaderCustomChange {
  private final ValueSetFhirImportService valueSetFhirImportService;

  public ValueSetFhirImport() {
    valueSetFhirImportService = BeanContext.getBean(ValueSetFhirImportService.class);
  }

  @Override
  protected void handleMigrationFile(String name, byte[] content) {
    log.info("Creating ValueSet " + name);

    ValueSet vs = JsonUtil.fromJson(asString(content), ValueSet.class);
    valueSetFhirImportService.importValueSet(vs, true);
  }
}
