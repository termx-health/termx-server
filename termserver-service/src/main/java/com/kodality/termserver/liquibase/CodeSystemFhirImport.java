package com.kodality.termserver.liquibase;

import com.kodality.commons.micronaut.BeanContext;
import com.kodality.commons.util.JsonUtil;
import com.kodality.termserver.auth.auth.SessionInfo;
import com.kodality.termserver.auth.auth.SessionStore;
import com.kodality.termserver.common.AuthorizedFileReaderCustomChange;
import com.kodality.termserver.fhir.codesystem.CodeSystemFhirImportService;
import com.kodality.zmei.fhir.resource.terminology.CodeSystem;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CodeSystemFhirImport extends AuthorizedFileReaderCustomChange {
  private final CodeSystemFhirImportService codeSystemFhirImportService;

  public CodeSystemFhirImport() {
    codeSystemFhirImportService = BeanContext.getBean(CodeSystemFhirImportService.class);
  }

  @Override
  protected void handleMigrationFile(String name, byte[] content) {
    log.info("updating codesystem " + name);
    try {
      SessionInfo sessionInfo = new SessionInfo();
      sessionInfo.setUsername("liquibase");
      sessionInfo.setRoles(List.of("kts-admin"));
      SessionStore.setLocal(sessionInfo);

      CodeSystem cs = JsonUtil.fromJson(asString(content), CodeSystem.class);
      codeSystemFhirImportService.importCodeSystem(cs);
    } finally {
      SessionStore.clearLocal();
    }
  }
}
