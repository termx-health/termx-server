package com.kodality.termserver.terminology.project.server;

import com.kodality.commons.util.JsonUtil;
import com.kodality.termserver.exception.ApiError;
import com.kodality.termserver.terminology.FhirClient;
import com.kodality.termserver.ts.project.projectpackage.PackageResourceType;
import com.kodality.termserver.ts.project.server.TerminologyServer;
import com.kodality.termserver.ts.project.server.TerminologyServerResourceRequest;
import com.kodality.termserver.ts.project.server.TerminologyServerResourceResponse;
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
    FhirClient client = new FhirClient(server.getRootUrl() + "/fhir");
    if (request.getResourceType().equals(PackageResourceType.code_system)) {
      CodeSystem codeSystem = client.<CodeSystem>read("CodeSystem", request.getResourceId()).join();
      return new TerminologyServerResourceResponse().setResource(JsonUtil.toPrettyJson(codeSystem));
    }
    if (request.getResourceType().equals(PackageResourceType.value_set)) {
      ValueSet valueSet = client.<ValueSet>read("ValueSet", request.getResourceId()).join();
      return new TerminologyServerResourceResponse().setResource(JsonUtil.toPrettyJson(valueSet));
    }
    if (request.getResourceType().equals(PackageResourceType.map_set)) {
      ConceptMap conceptMap = client.<ConceptMap>read("ConceptMap", request.getResourceId()).join();
      return new TerminologyServerResourceResponse().setResource(JsonUtil.toPrettyJson(conceptMap));
    }
    throw ApiError.TE902.toApiException();
  }
}
