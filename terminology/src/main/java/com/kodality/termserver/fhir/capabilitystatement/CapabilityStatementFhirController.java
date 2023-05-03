package com.kodality.termserver.fhir.capabilitystatement;

import com.kodality.commons.exception.NotFoundException;
import com.kodality.termserver.exception.ApiError;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller("/fhir")
@RequiredArgsConstructor
public class CapabilityStatementFhirController {
  private final CapabilityStatementFhirService service;

  @Get("/CapabilityStatement")
  public HttpResponse<?> getCapabilityStatement() {
    Object capabilityStatement = service.get();
    if (capabilityStatement == null) {
      throw new NotFoundException("CapabilityStatement not found");
    }
    return HttpResponse.ok(capabilityStatement);
  }

  @Get("/metadata{?params*}")
  public HttpResponse<?> getMetadata(Map<String, String> params) {
    if (!params.containsKey("mode") || !"terminology".equals(params.get("mode"))) {
      throw ApiError.TE906.toApiException();
    }
    Object capabilityStatement = service.get();
    if (capabilityStatement == null) {
      throw new NotFoundException("CapabilityStatement not found");
    }
    return HttpResponse.ok(capabilityStatement);
  }

}
