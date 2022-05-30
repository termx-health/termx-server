package com.kodality.termserver.fhir.codesystem;

import com.kodality.termserver.ApiError;
import com.kodality.termserver.codesystem.CodeSystemVersion;
import com.kodality.termserver.codesystem.Concept;
import com.kodality.termserver.codesystem.EntityProperty;
import com.kodality.termserver.common.BinaryHttpClient;
import com.kodality.termserver.common.CodeSystemImportService;
import com.kodality.zmei.fhir.FhirMapper;
import com.kodality.zmei.fhir.resource.infrastructure.Parameters;
import com.kodality.zmei.fhir.resource.infrastructure.Parameters.Parameter;
import io.micronaut.core.util.CollectionUtils;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class CodeSystemFhirImportService {
  private final CodeSystemImportService importService;
  private final BinaryHttpClient client = new BinaryHttpClient();

  public void importCodeSystems(Parameters parameters, List<String> warnings) {
    List<String> urls = CollectionUtils.isNotEmpty(parameters.getParameter()) ?
        parameters.getParameter().stream().filter(p -> "url".equals(p.getName())).map(Parameter::getValueString).toList() : Collections.emptyList();
    if (urls.isEmpty()) {
      throw ApiError.TE110.toApiException();
    }
    urls.forEach(url -> {
      try {
        importCodeSystem(url);
      } catch (Exception e) {
        warnings.add(String.format("CodeSystem from resource {%s} was not imported due to error: {%s}", url, e.getMessage()));
      }
    });
  }

  @Transactional
  public void importCodeSystem(String url) {
    String resource = getResource(url);
    com.kodality.zmei.fhir.resource.terminology.CodeSystem codeSystem =
        FhirMapper.fromJson(resource, com.kodality.zmei.fhir.resource.terminology.CodeSystem.class);

    CodeSystemVersion version = importService.prepareCodeSystemAndVersion(CodeSystemFhirImportMapper.mapCodeSystem(codeSystem));
    List<EntityProperty> properties = importService.prepareProperties(CodeSystemFhirImportMapper.mapProperties(codeSystem), codeSystem.getId());
    importService.prepareAssociationType(codeSystem.getHierarchyMeaning(), "code-system-hierarchy");

    List<Concept> concepts = CodeSystemFhirImportMapper.mapConcepts(codeSystem.getConcept(), codeSystem, properties, null);
    importService.importConcepts(concepts, version);
  }

  private String getResource(String url) {
    log.info("Loading fhir code system from {}", url);
    return new String(client.GET(url).body(), StandardCharsets.ISO_8859_1);
  }

}