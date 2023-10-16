package com.kodality.termx.terminology.terminology.codesystem.entity;

import com.kodality.termx.terminology.Privilege;
import com.kodality.termx.core.auth.Authorized;
import com.kodality.termx.core.auth.SessionStore;
import com.kodality.termx.ts.codesystem.CodeSystemEntityVersion;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import lombok.RequiredArgsConstructor;

@Controller("/ts/code-system-entity-versions")
@RequiredArgsConstructor
public class CodeSystemEntityVersionController {
  private final CodeSystemEntityVersionService codeSystemEntityVersionService;

  @Authorized(privilege = Privilege.CS_VIEW)
  @Get(uri = "/{id}")
  public CodeSystemEntityVersion getEntityVersion(@PathVariable Long id) {
    CodeSystemEntityVersion csev = codeSystemEntityVersionService.load(id);
    SessionStore.require().checkPermitted(csev.getCodeSystem(), Privilege.CS_VIEW);
    return csev;
  }
}
