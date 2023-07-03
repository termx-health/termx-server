package com.kodality.termx.terminology.mapset.entity;

import com.kodality.termx.ts.mapset.MapSetEntityVersion;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import lombok.RequiredArgsConstructor;

@Controller("/ts/map-set-entity-versions")
@RequiredArgsConstructor
public class MapSetEntityVersionController {
  private final MapSetEntityVersionService mapSetEntityVersionService;

  @Get(uri = "/{id}")
  public MapSetEntityVersion getEntityVersion(@PathVariable Long id) {
    return mapSetEntityVersionService.load(id);
  }
}
