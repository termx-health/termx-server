package com.kodality.termserver.ts.mapset.association;

import com.kodality.commons.exception.NotFoundException;
import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.association.AssociationType;
import com.kodality.termserver.mapset.MapSetAssociation;
import com.kodality.termserver.mapset.MapSetAssociationTypeQueryParams;
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
    return associationService.get(id).orElseThrow(() -> new NotFoundException("Map set association not found: " + id));
  }

  @Get(uri = "/types{?params*}")
  public QueryResult<AssociationType> queryAssociationTypes(MapSetAssociationTypeQueryParams params) {
    return associationService.queryAssociationTypes(params);
  }
}
