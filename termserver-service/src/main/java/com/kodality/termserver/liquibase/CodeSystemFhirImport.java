package com.kodality.termserver.liquibase;

import com.kodality.commons.micronaut.BeanContext;
import com.kodality.commons.micronaut.liquibase.FileReaderCustomChange;
import com.kodality.commons.util.JsonUtil;
import com.kodality.termserver.fhir.codesystem.CodeSystemFhirImportService;
import com.kodality.zmei.fhir.resource.terminology.CodeSystem;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CodeSystemFhirImport extends FileReaderCustomChange {
  private final CodeSystemFhirImportService codeSystemFhirImportService;

  public CodeSystemFhirImport() {
    codeSystemFhirImportService = BeanContext.getBean(CodeSystemFhirImportService.class);
  }

  @Override
  protected void handleFile(String name, byte[] content) {
    log.info("updating codesystem " + name);

    CodeSystem cs = JsonUtil.fromJson(asString(content), CodeSystem.class);
    codeSystemFhirImportService.importCodeSystem(cs);
  }
}
