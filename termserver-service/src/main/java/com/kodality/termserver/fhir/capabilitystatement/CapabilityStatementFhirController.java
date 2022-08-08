package com.kodality.termserver.fhir.capabilitystatement;

import com.kodality.commons.exception.NotFoundException;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller("/fhir/CapabilityStatement")
@RequiredArgsConstructor
public class CapabilityStatementFhirController {
  private final CapabilityStatementFhirService service;

  @Get()
  public HttpResponse<?> getCapabilityStatement() {
    Object capabilityStatement = service.get();
    if (capabilityStatement == null) {
      throw new NotFoundException("CapabilityStatement not found");
    }
    return HttpResponse.ok(capabilityStatement);
  }

}
