package com.kodality.termserver.integration.fhir.codesystem;

import com.kodality.termserver.codesystem.CodeSystemVersion;
import com.kodality.termserver.codesystem.Concept;
import com.kodality.termserver.codesystem.EntityProperty;
import com.kodality.termserver.integration.common.BinaryHttpClient;
import com.kodality.termserver.integration.common.CodeSystemImportService;
import com.kodality.zmei.fhir.FhirMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class FhirCodeSystemImportService {
  private final CodeSystemImportService importService;
  private final BinaryHttpClient client = new BinaryHttpClient();

  @Transactional
  public void importCodeSystem(String url) {
    String resource = getResource(url);
    com.kodality.zmei.fhir.resource.terminology.CodeSystem codeSystem = FhirMapper.fromJson(resource, com.kodality.zmei.fhir.resource.terminology.CodeSystem.class);

    CodeSystemVersion version = importService.prepareCodeSystemAndVersion(FhirCodeSystemMapper.mapCodeSystem(codeSystem));
    List<EntityProperty> properties = importService.prepareProperties(FhirCodeSystemMapper.mapProperties(codeSystem), codeSystem.getId());
    importService.prepareAssociationType(codeSystem.getHierarchyMeaning(), "code-system-hierarchy");

    List<Concept> concepts = FhirCodeSystemMapper.mapConcepts(codeSystem.getConcept(), codeSystem, properties, null);
    importService.importConcepts(concepts, version);
  }

  private String getResource(String url) {
    log.info("Loading fhir code system from {}", url);
    return new String(client.GET(url).body(), StandardCharsets.ISO_8859_1);
  }

}
