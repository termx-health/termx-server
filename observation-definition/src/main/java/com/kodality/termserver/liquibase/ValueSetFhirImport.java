package com.kodality.termserver.liquibase;

import com.kodality.commons.micronaut.BeanContext;
import com.kodality.commons.util.JsonUtil;
import com.kodality.termserver.file.AuthorizedFileReaderCustomChange;
import com.kodality.termserver.ts.ValueSetFhirImportProvider;
import com.kodality.zmei.fhir.resource.terminology.ValueSet;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ValueSetFhirImport extends AuthorizedFileReaderCustomChange {
  private final ValueSetFhirImportProvider fhirImportProvider;

  public ValueSetFhirImport() {
    fhirImportProvider = BeanContext.getBean(ValueSetFhirImportProvider.class);
  }

  @Override
  protected void handleMigrationFile(String name, byte[] content) {
    log.info("Creating ValueSet " + name);

    try {
      ValueSet vs = JsonUtil.fromJson(asString(content), ValueSet.class);
      fhirImportProvider.importValueSet(vs);
    } catch (Exception e) {
      log.error(e.getMessage());
    }
  }
}
