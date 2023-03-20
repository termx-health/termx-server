package com.kodality.termserver.ts.valueset;

import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import lombok.RequiredArgsConstructor;

@Controller("/ts/value-set-versions")
@RequiredArgsConstructor
public class ValueSetVersionController {
  private final ValueSetVersionService valueSetVersionService;

  @Get(uri = "/{id}")
  public ValueSetVersion getVersion(@PathVariable Long id) {
    return valueSetVersionService.load(id);
  }
}
