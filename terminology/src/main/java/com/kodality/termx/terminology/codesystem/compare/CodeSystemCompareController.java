package com.kodality.termx.terminology.codesystem.compare;

import com.kodality.termx.Privilege;
import com.kodality.termx.auth.Authorized;
import com.kodality.termx.auth.SessionStore;
import com.kodality.termx.terminology.codesystem.version.CodeSystemVersionService;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.QueryValue;
import lombok.RequiredArgsConstructor;

@Controller("/ts/code-systems/compare")
@RequiredArgsConstructor
public class CodeSystemCompareController {
  private final CodeSystemCompareService service;
  private final CodeSystemVersionService codeSystemVersionService;

  @Authorized(privilege = Privilege.CS_VIEW)
  @Get()
  public CodeSystemCompareResult compare(@QueryValue Long source, @QueryValue Long target) {
    SessionStore.require().checkPermitted(codeSystemVersionService.load(source).getCodeSystem(), Privilege.CS_VIEW);
    SessionStore.require().checkPermitted(codeSystemVersionService.load(target).getCodeSystem(), Privilege.CS_VIEW);
    return service.compare(source, target);
  }

}
