package com.kodality.termserver.ts.project.server;

import com.kodality.commons.util.JsonUtil;
import com.kodality.termserver.ApiError;
import com.kodality.termserver.auth.auth.SessionStore;
import com.kodality.termserver.ts.project.projectpackage.PackageResourceType;
import com.kodality.zmei.fhir.client.FhirClient;
import com.kodality.zmei.fhir.resource.DomainResource;
import com.kodality.zmei.fhir.resource.terminology.CodeSystem;
import com.kodality.zmei.fhir.resource.terminology.ConceptMap;
import com.kodality.zmei.fhir.resource.terminology.ValueSet;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class TerminologyServerResourceService {
  private final TerminologyServerService serverService;

  public TerminologyServerResourceResponse getResource(TerminologyServerResourceRequest request) {
    TerminologyServer server = serverService.load(request.getServerCode());
    if (request.getResourceType().equals(PackageResourceType.code_system)) {
      CodeSystem codeSystem = getClient(server.getRootUrl() + "/fhir/CodeSystem", CodeSystem.class).read(request.getResourceId()).join();
      return new TerminologyServerResourceResponse().setResource(JsonUtil.toPrettyJson(codeSystem));
    }
    if (request.getResourceType().equals(PackageResourceType.value_set)) {
      ValueSet valueSet = getClient(server.getRootUrl() + "/fhir/ValueSet", ValueSet.class).read(request.getResourceId()).join();
      return new TerminologyServerResourceResponse().setResource(JsonUtil.toPrettyJson(valueSet));
    }
    if (request.getResourceType().equals(PackageResourceType.map_set)) {
      ConceptMap conceptMap = getClient(server.getRootUrl() + "/fhir/MapSet", ConceptMap.class).read(request.getResourceId()).join();
      return new TerminologyServerResourceResponse().setResource(JsonUtil.toPrettyJson(conceptMap));
    }
    throw ApiError.TE902.toApiException();
  }

  public static <T extends DomainResource> FhirClient<T> getClient(String url, Class<T> cls) {
    return new FhirClient<>(url, cls, b -> b.header("Authorization", "Bearer " + SessionStore.require().getToken()));
  }
}
