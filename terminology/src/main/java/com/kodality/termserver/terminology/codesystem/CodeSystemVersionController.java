package com.kodality.termserver.terminology.codesystem;

import com.kodality.termserver.Privilege;
import com.kodality.termserver.auth.Authorized;
import com.kodality.termserver.ts.codesystem.CodeSystemVersion;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import lombok.RequiredArgsConstructor;

@Controller("/ts/code-system-versions")
@RequiredArgsConstructor
public class CodeSystemVersionController {
  private final CodeSystemVersionService codeSystemVersionService;

  @Authorized(Privilege.CS_VIEW)
  @Get(uri = "/{id}")
  public CodeSystemVersion getVersion(@PathVariable Long id) {
    return codeSystemVersionService.load(id);
  }
}
