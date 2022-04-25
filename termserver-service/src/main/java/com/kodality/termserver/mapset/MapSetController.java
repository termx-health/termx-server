package com.kodality.termserver.mapset;

import com.kodality.termserver.commons.model.exception.NotFoundException;
import com.kodality.termserver.commons.model.model.QueryResult;
import com.kodality.termserver.mapset.association.MapSetAssociationService;
import com.kodality.termserver.mapset.entity.MapSetEntityVersionService;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import java.util.List;
import javax.validation.Valid;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@Controller("/map-sets")
@RequiredArgsConstructor
public class MapSetController {
  private final MapSetService mapSetService;
  private final MapSetVersionService mapSetVersionService;
  private final MapSetAssociationService mapSetAssociationService;
  private final MapSetEntityVersionService mapSetEntityVersionService;

  @Get(uri = "{?params*}")
  public QueryResult<MapSet> getMapSets(MapSetQueryParams params) {
    return mapSetService.query(params);
  }

  @Get(uri = "/{id}")
  public MapSet getMapSet(@PathVariable String id) {
    return mapSetService.get(id).orElseThrow(() -> new NotFoundException("MapSet not found: " + id));
  }

  @Post
  public HttpResponse<?> create(@Body MapSet mapSet) {
    mapSetService.create(mapSet);
    return HttpResponse.created(mapSet);
  }

  @Get(uri = "/{mapSet}/versions")
  public List<MapSetVersion> getMapSetVersions(@PathVariable String mapSet) {
    return mapSetVersionService.getVersions(mapSet);
  }

  @Get(uri = "/{mapSet}/versions/{version}")
  public MapSetVersion getMapSetVersion(@PathVariable String mapSet, @PathVariable String version) {
    return mapSetVersionService.getVersion(mapSet, version).orElseThrow(() -> new NotFoundException("Map set version not found: " + version));
  }

  @Post(uri = "/{mapSet}/versions")
  public HttpResponse<?> createVersion(@PathVariable String mapSet, @Body @Valid MapSetVersion version) {
    version.setMapSet(mapSet);
    mapSetVersionService.save(version);
    return HttpResponse.created(version);
  }

  @Put(uri = "/{mapSet}/versions/{id}")
  public HttpResponse<?> updateVersion(@PathVariable String mapSet, @PathVariable Long id, @Body @Valid MapSetVersion version) {
    version.setId(id);
    version.setMapSet(mapSet);
    mapSetVersionService.save(version);
    return HttpResponse.created(version);
  }

  @Post(uri = "/{mapSet}/versions/{version}/activate")
  public HttpResponse<?> activateVersion(@PathVariable String mapSet, @PathVariable String version) {
    mapSetVersionService.activate(mapSet, version);
    return HttpResponse.noContent();
  }

  @Post(uri = "/{mapSet}/versions/{version}/retire")
  public HttpResponse<?> retireVersion(@PathVariable String mapSet, @PathVariable String version) {
    mapSetVersionService.retire(mapSet, version);
    return HttpResponse.noContent();
  }

  @Get(uri = "/{mapSet}/associations{?params*}")
  public QueryResult<MapSetAssociation> getAssociations(@PathVariable String mapSet, MapSetAssociationQueryParams params) {
    params.setMapSet(mapSet);
    return mapSetAssociationService.query(params);
  }

  @Get(uri = "/{mapSet}/associations/{id}")
  public MapSetAssociation getAssociation(@PathVariable String mapSet, @PathVariable Long id) {
    return mapSetAssociationService.get(mapSet, id).orElseThrow(() -> new NotFoundException("Association not found: " + id));
  }

  @Get(uri = "/{mapSet}/versions/{version}/associations{?params*}")
  public QueryResult<MapSetAssociation> getAssociations(@PathVariable String mapSet, @PathVariable String version, MapSetAssociationQueryParams params) {
    params.setMapSet(mapSet);
    params.setMapSetVersion(version);
    return mapSetAssociationService.query(params);
  }

  @Get(uri = "/{mapSet}/versions/{version}/associations/{id}")
  public MapSetAssociation getAssociation(@PathVariable String mapSet, @PathVariable String version, @PathVariable Long id) {
    return mapSetAssociationService.get(mapSet, version, id).orElseThrow(() -> new NotFoundException("Association not found: " + id));
  }

  @Post(uri = "/{mapSet}/associations")
  public HttpResponse<?> createAssociation(@PathVariable String mapSet, @Body @Valid MapSetAssociation association) {
    association.setMapSet(mapSet);
    mapSetAssociationService.save(association, mapSet);
    return HttpResponse.created(association);
  }

  @Get(uri = "/{mapSet}/versions/{version}/entity-versions")
  public List<MapSetEntityVersion> getEntityVersions(@PathVariable String mapSet, @PathVariable String version) {
    MapSetEntityVersionQueryParams params = new MapSetEntityVersionQueryParams();
    params.setMapSet(mapSet);
    params.setMapSetVersion(version);
    return mapSetEntityVersionService.query(params).getData();
  }

  @Post(uri = "/{mapSet}/versions/{version}/entity-versions")
  public HttpResponse<?> saveEntityVersions(@PathVariable String mapSet, @PathVariable String version, @Body EntityVersionRequest request) {
    mapSetVersionService.saveEntityVersions(mapSet, version, request.getVersions());
    return HttpResponse.ok();
  }

  @Getter
  @Setter
  private static class EntityVersionRequest {
    private List<MapSetEntityVersion> versions;
  }
}
