package com.kodality.termx.core.sys.provenance;

import com.kodality.termx.core.auth.Authorized;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.QueryValue;
import java.util.List;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Controller("/provenances")
public class ProvenanceController {
  private final ProvenanceService service;

  @Authorized("Provenance.view")
  @Get()
  public List<Provenance> query(@QueryValue String target) {
    return service.find(target);
  }

}
