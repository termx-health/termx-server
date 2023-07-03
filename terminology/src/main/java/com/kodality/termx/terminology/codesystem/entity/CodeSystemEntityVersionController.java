package com.kodality.termx.terminology.codesystem.entity;

import com.kodality.termx.Privilege;
import com.kodality.termx.auth.Authorized;
import com.kodality.termx.ts.codesystem.CodeSystemEntityVersion;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import lombok.RequiredArgsConstructor;

@Controller("/ts/code-system-entity-versions")
@RequiredArgsConstructor
public class CodeSystemEntityVersionController {
  private final CodeSystemEntityVersionService codeSystemEntityVersionService;

  @Authorized(Privilege.CS_VIEW)
  @Get(uri = "/{id}")
  public CodeSystemEntityVersion getEntityVersion(@PathVariable Long id) {
    return codeSystemEntityVersionService.load(id);
  }
}
