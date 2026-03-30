package org.termx.terminology.liquibase;

import com.kodality.commons.micronaut.BeanContext;
import com.kodality.zmei.fhir.FhirMapper;
import org.termx.terminology.fhir.valueset.ValueSetFhirImportService;
import org.termx.core.file.AuthorizedFileReaderCustomChange;
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

    try {
      ValueSet vs = FhirMapper.fromJson(asString(content), ValueSet.class);
      valueSetFhirImportService.importValueSet(vs);
    } catch (Exception e) {
      log.error("Failed to create ValueSet {} during Liquibase import", name, e);
    }
  }
}
