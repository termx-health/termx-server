package com.kodality.termserver.liquibase;

import com.kodality.commons.micronaut.BeanContext;
import com.kodality.commons.util.JsonUtil;
import com.kodality.termserver.AuthorizedFileReaderCustomChange;
import com.kodality.termserver.fhir.codesystem.CodeSystemFhirImportService;
import com.kodality.zmei.fhir.resource.terminology.CodeSystem;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CodeSystemFhirImport extends AuthorizedFileReaderCustomChange {
  private final CodeSystemFhirImportService codeSystemFhirImportService;

  public CodeSystemFhirImport() {
    codeSystemFhirImportService = BeanContext.getBean(CodeSystemFhirImportService.class);
  }

  @Override
  protected void handleMigrationFile(String name, byte[] content) {
    log.info("Creating CodeSystem " + name);

    try {
      CodeSystem cs = JsonUtil.fromJson(asString(content), CodeSystem.class);
      codeSystemFhirImportService.importCodeSystem(cs);
    } catch (Exception e) {
      log.error(e.getMessage());
    }
  }
}
