package com.kodality.termserver.terminology.project.projectpackage.resource;

import com.kodality.termserver.auth.SessionStore;
import com.kodality.termserver.exception.ApiError;
import com.kodality.termserver.fhir.codesystem.CodeSystemFhirImportService;
import com.kodality.termserver.fhir.conceptmap.ConceptMapFhirImportService;
import com.kodality.termserver.fhir.valueset.ValueSetFhirImportService;
import com.kodality.termserver.terminology.project.server.TerminologyServerService;
import com.kodality.termserver.ts.project.projectpackage.PackageResourceType;
import com.kodality.termserver.ts.project.projectpackage.PackageVersion.PackageResource;
import com.kodality.termserver.ts.project.server.TerminologyServer;
import com.kodality.zmei.fhir.client.FhirClient;
import com.kodality.zmei.fhir.resource.DomainResource;
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
    if (resourceType.equals(PackageResourceType.code_system)) {
      CodeSystem codeSystem = getClient(server.getRootUrl() + "/fhir/CodeSystem", CodeSystem.class).read(resourceId).join();
      csFhirImportService.importCodeSystem(codeSystem);
    } else if (resourceType.equals(PackageResourceType.value_set)) {
      ValueSet valueSet = getClient(server.getRootUrl() + "/fhir/ValueSet", ValueSet.class).read(resourceId).join();
      vsFhirImportService.importValueSet(valueSet, false);
    } else if (resourceType.equals(PackageResourceType.map_set)) {
      ConceptMap conceptMap = getClient(server.getRootUrl() + "/fhir/MapSet", ConceptMap.class).read(resourceId).join();
      cmFhirImportService.importMapSet(conceptMap, false);
    } else {
      throw ApiError.TE902.toApiException();
    }
  }


  public static <T extends DomainResource> FhirClient<T> getClient(String url, Class<T> cls) {
    return new FhirClient<>(url, cls, b -> b.header("Authorization", "Bearer " + SessionStore.require().getToken()));
  }
}
