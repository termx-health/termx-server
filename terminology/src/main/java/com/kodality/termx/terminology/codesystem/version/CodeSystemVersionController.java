package com.kodality.termx.terminology.codesystem.version;

import com.kodality.termx.Privilege;
import com.kodality.termx.auth.Authorized;
import com.kodality.termx.auth.SessionStore;
import com.kodality.termx.ts.codesystem.CodeSystemVersion;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import lombok.RequiredArgsConstructor;

@Controller("/ts/code-system-versions")
@RequiredArgsConstructor
public class CodeSystemVersionController {
  private final CodeSystemVersionService codeSystemVersionService;

  @Authorized(privilege = Privilege.CS_VIEW)
  @Get(uri = "/{id}")
  public CodeSystemVersion getVersion(@PathVariable Long id) {
    CodeSystemVersion version = codeSystemVersionService.load(id);
    SessionStore.require().checkPermitted(version.getCodeSystem(), Privilege.CS_VIEW);
    return version;
  }
}
