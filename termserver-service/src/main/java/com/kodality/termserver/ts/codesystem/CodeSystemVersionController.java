package com.kodality.termserver.ts.codesystem;

import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import lombok.RequiredArgsConstructor;

@Controller("/ts/code-system-versions")
@RequiredArgsConstructor
public class CodeSystemVersionController {
  private final CodeSystemVersionService codeSystemVersionService;

  @Get(uri = "/{id}")
  public CodeSystemVersion getVersion(@PathVariable Long id) {
    return codeSystemVersionService.load(id);
  }
}
