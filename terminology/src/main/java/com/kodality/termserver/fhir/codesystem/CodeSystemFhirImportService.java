package com.kodality.termserver.fhir.codesystem;

import com.kodality.termserver.http.BinaryHttpClient;
import com.kodality.termserver.terminology.codesystem.CodeSystemImportService;
import com.kodality.termserver.ts.PublicationStatus;
import com.kodality.termserver.ts.association.AssociationKind;
import com.kodality.termserver.ts.association.AssociationType;
import com.kodality.zmei.fhir.FhirMapper;
import com.kodality.zmei.fhir.resource.Resource;
import com.kodality.zmei.fhir.resource.other.Bundle;
import com.kodality.zmei.fhir.resource.terminology.CodeSystem;
import jakarta.inject.Singleton;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class CodeSystemFhirImportService {
  private final CodeSystemImportService importService;
  private final BinaryHttpClient client = new BinaryHttpClient();

  @Transactional
  public void importCodeSystem(com.kodality.zmei.fhir.resource.terminology.CodeSystem codeSystem) {
    List<AssociationType> associationTypes = List.of(new AssociationType("is-a", AssociationKind.codesystemHierarchyMeaning, true));
    importService.importCodeSystem(CodeSystemFhirImportMapper.mapCodeSystem(codeSystem), associationTypes,
        PublicationStatus.active.equals(codeSystem.getStatus()));
  }

  public void importCodeSystemFromUrl(String url, String codeSystemId) {
    String resource = getResource(url);
    importCodeSystem(resource, codeSystemId);
  }

  public void importCodeSystem(String resource, String codeSystemId) {
    Resource res = FhirMapper.fromJson(resource, Resource.class);
    if ("Bundle".equals(res.getResourceType())) {
      Bundle bundle = FhirMapper.fromJson(resource, Bundle.class);
      bundle.getEntry().forEach(e -> importCodeSystem((CodeSystem) e.getResource()));
    } else {
      com.kodality.zmei.fhir.resource.terminology.CodeSystem codeSystem = FhirMapper.fromJson(resource, com.kodality.zmei.fhir.resource.terminology.CodeSystem.class);
      codeSystem.setId(codeSystemId);
      importCodeSystem(codeSystem);
    }
  }

  private String getResource(String url) {
    log.info("Loading fhir code system from {}", url);
    return new String(client.GET(url).body(), StandardCharsets.UTF_8);
  }
}
