package com.kodality.termx.terminology.codesystem.compare;

import com.kodality.termx.Privilege;
import com.kodality.termx.auth.Authorized;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.QueryValue;
import lombok.RequiredArgsConstructor;

@Controller("/ts/code-systems/compare")
@RequiredArgsConstructor
public class CodeSystemCompareController {
  private final CodeSystemCompareService service;

  @Authorized(Privilege.CS_VIEW)
  @Get()
  public CodeSystemCompareResult compare(@QueryValue Long source, @QueryValue Long target) {
    return service.compare(source, target);
  }

}
