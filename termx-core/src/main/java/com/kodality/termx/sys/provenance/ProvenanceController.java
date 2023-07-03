package com.kodality.termx.sys.provenance;

import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.QueryValue;
import java.util.List;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Controller("/provenances")
public class ProvenanceController {
  private final ProvenanceService service;

  @Get()
  public List<Provenance> query(@QueryValue String target) {
    return service.find(target);
  }

}
