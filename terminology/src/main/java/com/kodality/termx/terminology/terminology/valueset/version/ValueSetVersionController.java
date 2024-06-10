package com.kodality.termx.terminology.terminology.valueset.version;

import com.kodality.termx.terminology.Privilege;
import com.kodality.termx.core.auth.Authorized;
import com.kodality.termx.ts.valueset.ValueSetVersion;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import lombok.RequiredArgsConstructor;

@Controller("/ts/value-set-versions")
@RequiredArgsConstructor
public class ValueSetVersionController {
  private final ValueSetVersionService valueSetVersionService;

  @Authorized(Privilege.VS_VIEW)
  @Get(uri = "/{id}")
  public ValueSetVersion getVersion(@PathVariable Long id) {
    return valueSetVersionService.load(id);
  }
}
