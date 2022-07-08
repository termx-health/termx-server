package com.kodality.termserver.ts.mapset.association;

import com.kodality.commons.exception.NotFoundException;
import com.kodality.termserver.mapset.MapSetAssociation;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import lombok.RequiredArgsConstructor;

@Controller("/ts/map-set-associations")
@RequiredArgsConstructor
public class MapSetAssociationController {

  private final MapSetAssociationService associationService;

  @Get(uri = "/{id}")
  public MapSetAssociation getAssociation(@PathVariable Long id) {
    return associationService.load(id).orElseThrow(() -> new NotFoundException("Map set association not found: " + id));
  }
}
