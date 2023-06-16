package com.kodality.termserver.terminology.space.spacepackage.resource;

import com.kodality.termserver.exception.ApiError;
import com.kodality.termserver.fhir.codesystem.CodeSystemFhirImportService;
import com.kodality.termserver.fhir.conceptmap.ConceptMapFhirImportService;
import com.kodality.termserver.fhir.valueset.ValueSetFhirImportService;
import com.kodality.termserver.terminology.space.server.TerminologyServerService;
import com.kodality.termserver.ts.space.spacepackage.PackageResourceType;
import com.kodality.termserver.ts.space.spacepackage.PackageVersion.PackageResource;
import com.kodality.termserver.ts.space.server.TerminologyServer;
import com.kodality.zmei.fhir.client.FhirClient;
import com.kodality.zmei.fhir.resource.terminology.CodeSystem;
import com.kodality.zmei.fhir.resource.terminology.ConceptMap;
import com.kodality.zmei.fhir.resource.terminology.ValueSet;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class PackageResourceSyncService {
  private final PackageResourceService packageResourceService;
  private final TerminologyServerService terminologyServerService;
  private final CodeSystemFhirImportService csFhirImportService;
  private final ValueSetFhirImportService vsFhirImportService;
  private final ConceptMapFhirImportService cmFhirImportService;

  @Transactional
  public void sync(Long id) {
    PackageResource resource = packageResourceService.load(id);
    if (resource == null || resource.getTerminologyServer() == null) {
      return;
    }
    String resourceType = resource.getResourceType();
    String resourceId = resource.getResourceId();
    TerminologyServer server = terminologyServerService.load(resource.getTerminologyServer());
    FhirClient client = new FhirClient(server.getRootUrl() + "/fhir");
    if (resourceType.equals(PackageResourceType.code_system)) {
      CodeSystem codeSystem = client.<CodeSystem>read("CodeSystem", resourceId).join();
      csFhirImportService.importCodeSystem(codeSystem);
    } else if (resourceType.equals(PackageResourceType.value_set)) {
      ValueSet valueSet = client.<ValueSet>read("ValueSet", resourceId).join();
      vsFhirImportService.importValueSet(valueSet, false);
    } else if (resourceType.equals(PackageResourceType.map_set)) {
      ConceptMap conceptMap = client.<ConceptMap>read("ConceptMap", resourceId).join();
      cmFhirImportService.importMapSet(conceptMap);
    } else {
      throw ApiError.TE902.toApiException();
    }
  }
}
