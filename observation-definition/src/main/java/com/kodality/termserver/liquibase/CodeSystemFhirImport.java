package com.kodality.termserver.liquibase;

import com.kodality.commons.micronaut.BeanContext;
import com.kodality.commons.util.JsonUtil;
import com.kodality.termserver.file.AuthorizedFileReaderCustomChange;
import com.kodality.termserver.ts.CodeSystemFhirImportProvider;
import com.kodality.zmei.fhir.resource.terminology.CodeSystem;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CodeSystemFhirImport extends AuthorizedFileReaderCustomChange {
  private final CodeSystemFhirImportProvider fhirImportProvider;

  public CodeSystemFhirImport() {
    fhirImportProvider = BeanContext.getBean(CodeSystemFhirImportProvider.class);
  }

  @Override
  protected void handleMigrationFile(String name, byte[] content) {
    log.info("Creating CodeSystem " + name);

    try {
      CodeSystem cs = JsonUtil.fromJson(asString(content), CodeSystem.class);
      fhirImportProvider.importCodeSystem(cs);
    } catch (Exception e) {
      log.error(e.getMessage());
    }
  }
}
