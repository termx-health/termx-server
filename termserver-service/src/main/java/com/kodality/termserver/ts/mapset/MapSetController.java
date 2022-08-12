package com.kodality.termserver.ts.mapset;

import com.kodality.commons.exception.NotFoundException;
import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.mapset.MapSet;
import com.kodality.termserver.mapset.MapSetAssociation;
import com.kodality.termserver.mapset.MapSetAssociationQueryParams;
import com.kodality.termserver.mapset.MapSetEntityVersion;
import com.kodality.termserver.mapset.MapSetEntityVersionQueryParams;
import com.kodality.termserver.mapset.MapSetQueryParams;
import com.kodality.termserver.mapset.MapSetVersion;
import com.kodality.termserver.mapset.MapSetVersionQueryParams;
import com.kodality.termserver.ts.mapset.association.MapSetAssociationService;
import com.kodality.termserver.ts.mapset.entity.MapSetEntityVersionService;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;

@Controller("/ts/map-sets")
@RequiredArgsConstructor
public class MapSetController {
  private final MapSetService mapSetService;
  private final MapSetVersionService mapSetVersionService;
  private final MapSetAssociationService mapSetAssociationService;
  private final MapSetEntityVersionService mapSetEntityVersionService;

  //----------------MapSet----------------

  @Get(uri = "{?params*}")
  public QueryResult<MapSet> queryMapSets(MapSetQueryParams params) {
    return mapSetService.query(params);
  }

  @Get(uri = "/{mapSet}")
  public MapSet getMapSet(@PathVariable String mapSet) {
    return mapSetService.load(mapSet).orElseThrow(() -> new NotFoundException("MapSet not found: " + mapSet));
  }

  @Post
  public HttpResponse<?> saveMapSet(@Body @Valid MapSet mapSet) {
    mapSetService.save(mapSet);
    return HttpResponse.created(mapSet);
  }

  //----------------MapSet Version----------------

  @Get(uri = "/{mapSet}/versions{?params*}")
  public QueryResult<MapSetVersion> queryMapSetVersions(@PathVariable String mapSet, MapSetVersionQueryParams params) {
    params.setMapSet(mapSet);
    return mapSetVersionService.query(params);
  }

  @Get(uri = "/{mapSet}/versions/{version}")
  public MapSetVersion getMapSetVersion(@PathVariable String mapSet, @PathVariable String version) {
    return mapSetVersionService.load(mapSet, version).orElseThrow(() -> new NotFoundException("Map set version not found: " + version));
  }

  @Post(uri = "/{mapSet}/versions")
  public HttpResponse<?> createVersion(@PathVariable String mapSet, @Body @Valid MapSetVersion version) {
    version.setId(null);
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

  //----------------MapSet Association----------------

  @Get(uri = "/{mapSet}/associations{?params*}")
  public QueryResult<MapSetAssociation> queryAssociations(@PathVariable String mapSet, MapSetAssociationQueryParams params) {
    params.setMapSet(mapSet);
    return mapSetAssociationService.query(params);
  }

  @Get(uri = "/{mapSet}/associations/{id}")
  public MapSetAssociation getAssociation(@PathVariable String mapSet, @PathVariable Long id) {
    return mapSetAssociationService.load(mapSet, id).orElseThrow(() -> new NotFoundException("Association not found: " + id));
  }

  @Get(uri = "/{mapSet}/versions/{version}/associations/{id}")
  public MapSetAssociation getAssociation(@PathVariable String mapSet, @PathVariable String version, @PathVariable Long id) {
    return mapSetAssociationService.load(mapSet, version, id).orElseThrow(() -> new NotFoundException("Association not found: " + id));
  }

  @Post(uri = "/{mapSet}/associations")
  public HttpResponse<?> createAssociation(@PathVariable String mapSet, @Body @Valid MapSetAssociation association) {
    association.setId(null);
    mapSetAssociationService.save(association, mapSet);
    return HttpResponse.created(association);
  }

  @Put(uri = "/{mapSet}/associations/{id}")
  public HttpResponse<?> updateAssociation(@PathVariable String mapSet, @PathVariable Long id, @Body @Valid MapSetAssociation association) {
    association.setId(id);
    mapSetAssociationService.save(association, mapSet);
    return HttpResponse.created(association);
  }

  //----------------MapSet EntityVersion----------------

  @Get(uri = "/{mapSet}/entity-versions{?params*}")
  public QueryResult<MapSetEntityVersion> searchEntityVersions(@PathVariable String mapSet, MapSetEntityVersionQueryParams params) {
    params.setMapSet(mapSet);
    return mapSetEntityVersionService.query(params);
  }

  @Post(uri = "/{mapSet}/entities/{entityId}/versions")
  public HttpResponse<?> createEntityVersion(@PathVariable String mapSet, @PathVariable Long entityId, @Body @Valid MapSetEntityVersion version) {
    version.setId(null);
    version.setMapSet(mapSet);
    mapSetEntityVersionService.save(version, entityId);
    return HttpResponse.created(version);
  }

  @Put(uri = "/{mapSet}/entities/{entityId}/versions/{id}")
  public HttpResponse<?> updateVersion(@PathVariable String mapSet, @PathVariable Long entityId, @PathVariable Long id,  @Body @Valid MapSetEntityVersion version) {
    version.setId(id);
    version.setMapSet(mapSet);
    mapSetEntityVersionService.save(version, entityId);
    return HttpResponse.created(version);
  }

  @Post(uri = "/{mapSet}/entities/versions/{id}/activate")
  public HttpResponse<?> activateVersion(@PathVariable String mapSet, @PathVariable Long id) {
    mapSetEntityVersionService.activate(id);
    return HttpResponse.noContent();
  }

  @Post(uri = "/{mapSet}/entities/versions/{id}/retire")
  public HttpResponse<?> retireVersion(@PathVariable String mapSet, @PathVariable Long id) {
    mapSetEntityVersionService.retire(id);
    return HttpResponse.noContent();
  }

  @Post(uri = "/{mapSet}/versions/{version}/entity-versions/{entityVersionId}/membership")
  public HttpResponse<?> linkEntityVersion(@PathVariable String mapSet, @PathVariable String version, @PathVariable Long entityVersionId) {
    mapSetVersionService.linkEntityVersion(mapSet, version, entityVersionId);
    return HttpResponse.ok();
  }

  @Delete(uri = "/{mapSet}/versions/{version}/entity-versions/{entityVersionId}/membership")
  public HttpResponse<?> unlinkEntityVersion(@PathVariable String mapSet, @PathVariable String version, @PathVariable Long entityVersionId) {
    mapSetVersionService.unlinkEntityVersion(mapSet, version, entityVersionId);
    return HttpResponse.ok();
  }
}
