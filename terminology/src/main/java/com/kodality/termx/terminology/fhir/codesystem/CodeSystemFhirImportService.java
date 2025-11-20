package com.kodality.termx.terminology.fhir.codesystem;

import com.kodality.termx.core.http.BinaryHttpClient;
import com.kodality.termx.terminology.ApiError;
import com.kodality.termx.terminology.terminology.codesystem.CodeSystemImportService;
import com.kodality.termx.ts.PublicationStatus;
import com.kodality.termx.ts.association.AssociationKind;
import com.kodality.termx.ts.association.AssociationType;
import com.kodality.termx.ts.codesystem.CodeSystemImportAction;
import com.kodality.zmei.fhir.FhirMapper;
import com.kodality.zmei.fhir.resource.Resource;
import com.kodality.zmei.fhir.resource.ResourceType;
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
    if (!ResourceType.codeSystem.equals(codeSystem.getResourceType())) {
      throw ApiError.TE107.toApiException();
    }
    List<AssociationType> associationTypes = List.of(new AssociationType("is-a", AssociationKind.codesystemHierarchyMeaning, true));
    CodeSystemImportAction action = new CodeSystemImportAction()
        .setActivate(PublicationStatus.active.equals(codeSystem.getStatus()))
        .setRetire(PublicationStatus.retired.equals(codeSystem.getStatus()))
        .setCleanRun(true);
    importService.importCodeSystem(CodeSystemFhirMapper.fromFhirCodeSystem(codeSystem), associationTypes, action);
  }

  public void importCodeSystemFromUrl(String url, String codeSystemId) {
    String resource = getResource(url);
    importCodeSystem(resource, codeSystemId);
  }

  public void importCodeSystem(String resource, String codeSystemId) {
    Resource res = FhirMapper.fromJson(resource, Resource.class);
    if ("Bundle".equals(res.getResourceType())) {
      Bundle bundle = FhirMapper.fromJson(resource, Bundle.class);
      // TODO: there might be a bug here if multiple code systems are in the bundle and one before is a supplement of the later
      // I should test this out
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
