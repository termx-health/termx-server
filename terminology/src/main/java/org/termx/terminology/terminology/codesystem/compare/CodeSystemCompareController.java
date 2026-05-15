package org.termx.terminology.terminology.codesystem.compare;

import org.termx.terminology.Privilege;
import org.termx.core.auth.Authorized;
import org.termx.core.auth.SessionStore;
import org.termx.terminology.terminology.codesystem.version.CodeSystemVersionService;
import org.termx.ts.codesystem.CodeSystemCompareResult;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.QueryValue;
import lombok.RequiredArgsConstructor;

@Controller("/ts/code-systems/compare")
@RequiredArgsConstructor
public class CodeSystemCompareController {
  private final CodeSystemCompareService service;
  private final CodeSystemVersionService codeSystemVersionService;

  @Authorized(privilege = Privilege.CS_READ)
  @Get()
  public CodeSystemCompareResult compare(@QueryValue Long source, @QueryValue Long target) {
    SessionStore.require().checkPermitted(codeSystemVersionService.load(source).getCodeSystem(), Privilege.CS_READ);
    SessionStore.require().checkPermitted(codeSystemVersionService.load(target).getCodeSystem(), Privilege.CS_READ);
    return service.compare(source, target);
  }

}
