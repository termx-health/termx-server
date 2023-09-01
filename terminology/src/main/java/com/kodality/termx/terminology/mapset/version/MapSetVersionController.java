package com.kodality.termx.terminology.mapset.version;

import com.kodality.termx.Privilege;
import com.kodality.termx.auth.Authorized;
import com.kodality.termx.ts.mapset.MapSetVersion;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import lombok.RequiredArgsConstructor;

@Controller("/ts/map-set-versions")
@RequiredArgsConstructor
public class MapSetVersionController {
  private final MapSetVersionService mapSetVersionService;

  @Authorized(Privilege.VS_VIEW)
  @Get(uri = "/{id}")
  public MapSetVersion getVersion(@PathVariable Long id) {
    return mapSetVersionService.load(id);
  }
}
