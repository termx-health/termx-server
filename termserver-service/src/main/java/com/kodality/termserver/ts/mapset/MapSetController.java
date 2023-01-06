package com.kodality.termserver.ts.mapset;

import com.kodality.commons.exception.NotFoundException;
import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.auth.auth.Authorized;
import com.kodality.termserver.auth.auth.ResourceId;
import com.kodality.termserver.auth.auth.UserPermissionService;
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

  private final UserPermissionService userPermissionService;

  //----------------MapSet----------------

  @Authorized("*.MapSet.view")
  @Get(uri = "{?params*}")
  public QueryResult<MapSet> queryMapSets(MapSetQueryParams params) {
    params.setPermittedIds(userPermissionService.getPermittedResourceIds("MapSet", "view"));
    return mapSetService.query(params);
  }

  @Authorized("*.MapSet.view")
  @Get(uri = "/{mapSet}")
  public MapSet getMapSet(@PathVariable @ResourceId String mapSet) {
    return mapSetService.load(mapSet).orElseThrow(() -> new NotFoundException("MapSet not found: " + mapSet));
  }

  @Authorized("*.MapSet.edit")
  @Post
  public HttpResponse<?> saveMapSet(@Body @Valid MapSet mapSet) {
    mapSetService.save(mapSet);
    return HttpResponse.created(mapSet);
  }

  @Authorized("*.MapSet.publish")
  @Delete(uri = "/{mapSet}")
  public HttpResponse<?> deleteMapSet(@PathVariable @ResourceId String mapSet) {
    mapSetService.cancel(mapSet);
    return HttpResponse.ok();
  }

  //----------------MapSet Version----------------

  @Authorized("*.MapSet.view")
  @Get(uri = "/{mapSet}/versions{?params*}")
  public QueryResult<MapSetVersion> queryMapSetVersions(@PathVariable @ResourceId String mapSet, MapSetVersionQueryParams params) {
    params.setPermittedMapSets(userPermissionService.getPermittedResourceIds("MapSet", "view"));
    params.setMapSet(mapSet);
    return mapSetVersionService.query(params);
  }

  @Authorized("*.MapSet.view")
  @Get(uri = "/{mapSet}/versions/{version}")
  public MapSetVersion getMapSetVersion(@PathVariable @ResourceId String mapSet, @PathVariable String version) {
    return mapSetVersionService.load(mapSet, version).orElseThrow(() -> new NotFoundException("Map set version not found: " + version));
  }

  @Authorized("*.MapSet.edit")
  @Post(uri = "/{mapSet}/versions")
  public HttpResponse<?> createVersion(@PathVariable @ResourceId String mapSet, @Body @Valid MapSetVersion version) {
    version.setId(null);
    version.setMapSet(mapSet);
    mapSetVersionService.save(version);
    return HttpResponse.created(version);
  }

  @Authorized("*.MapSet.edit")
  @Put(uri = "/{mapSet}/versions/{id}")
  public HttpResponse<?> updateVersion(@PathVariable @ResourceId String mapSet, @PathVariable Long id, @Body @Valid MapSetVersion version) {
    version.setId(id);
    version.setMapSet(mapSet);
    mapSetVersionService.save(version);
    return HttpResponse.created(version);
  }

  @Authorized("*.MapSet.publish")
  @Post(uri = "/{mapSet}/versions/{version}/activate")
  public HttpResponse<?> activateVersion(@PathVariable @ResourceId String mapSet, @PathVariable String version) {
    mapSetVersionService.activate(mapSet, version);
    return HttpResponse.noContent();
  }

  @Authorized("*.MapSet.publish")
  @Post(uri = "/{mapSet}/versions/{version}/retire")
  public HttpResponse<?> retireVersion(@PathVariable @ResourceId String mapSet, @PathVariable String version) {
    mapSetVersionService.retire(mapSet, version);
    return HttpResponse.noContent();
  }

  //----------------MapSet Association----------------

  @Authorized("*.MapSet.view")
  @Get(uri = "/{mapSet}/associations{?params*}")
  public QueryResult<MapSetAssociation> queryAssociations(@PathVariable @ResourceId String mapSet, MapSetAssociationQueryParams params) {
    params.setPermittedMapSets(userPermissionService.getPermittedResourceIds("MapSet", "view"));
    params.setMapSet(mapSet);
    return mapSetAssociationService.query(params);
  }

  @Authorized("*.MapSet.view")
  @Get(uri = "/{mapSet}/associations/{id}")
  public MapSetAssociation getAssociation(@PathVariable @ResourceId String mapSet, @PathVariable Long id) {
    return mapSetAssociationService.load(mapSet, id).orElseThrow(() -> new NotFoundException("Association not found: " + id));
  }

  @Authorized("*.MapSet.view")
  @Get(uri = "/{mapSet}/versions/{version}/associations/{id}")
  public MapSetAssociation getAssociation(@PathVariable @ResourceId String mapSet, @PathVariable String version, @PathVariable Long id) {
    return mapSetAssociationService.load(mapSet, version, id).orElseThrow(() -> new NotFoundException("Association not found: " + id));
  }

  @Authorized("*.MapSet.edit")
  @Post(uri = "/{mapSet}/associations")
  public HttpResponse<?> createAssociation(@PathVariable @ResourceId String mapSet, @Body @Valid MapSetAssociation association) {
    association.setId(null);
    mapSetAssociationService.save(association, mapSet);
    return HttpResponse.created(association);
  }

  @Authorized("*.MapSet.edit")
  @Put(uri = "/{mapSet}/associations/{id}")
  public HttpResponse<?> updateAssociation(@PathVariable @ResourceId String mapSet, @PathVariable Long id, @Body @Valid MapSetAssociation association) {
    association.setId(id);
    mapSetAssociationService.save(association, mapSet);
    return HttpResponse.created(association);
  }

  //----------------MapSet EntityVersion----------------

  @Authorized("*.MapSet.view")
  @Get(uri = "/{mapSet}/entity-versions{?params*}")
  public QueryResult<MapSetEntityVersion> queryEntityVersions(@PathVariable @ResourceId String mapSet, MapSetEntityVersionQueryParams params) {
    params.setPermittedMapSets(userPermissionService.getPermittedResourceIds("MapSet", "view"));
    params.setMapSet(mapSet);
    return mapSetEntityVersionService.query(params);
  }

  @Authorized("*.MapSet.edit")
  @Post(uri = "/{mapSet}/entities/{entityId}/versions")
  public HttpResponse<?> createEntityVersion(@PathVariable @ResourceId String mapSet, @PathVariable Long entityId, @Body @Valid MapSetEntityVersion version) {
    version.setId(null);
    version.setMapSet(mapSet);
    mapSetEntityVersionService.save(version, entityId);
    return HttpResponse.created(version);
  }

  @Authorized("*.MapSet.edit")
  @Put(uri = "/{mapSet}/entities/{entityId}/versions/{id}")
  public HttpResponse<?> updateVersion(@PathVariable @ResourceId String mapSet, @PathVariable Long entityId, @PathVariable Long id,  @Body @Valid MapSetEntityVersion version) {
    version.setId(id);
    version.setMapSet(mapSet);
    mapSetEntityVersionService.save(version, entityId);
    return HttpResponse.created(version);
  }

  @Authorized("*.MapSet.publish")
  @Post(uri = "/{mapSet}/entities/versions/{id}/activate")
  public HttpResponse<?> activateVersion(@PathVariable @ResourceId String mapSet, @PathVariable Long id) {
    mapSetEntityVersionService.activate(id);
    return HttpResponse.noContent();
  }

  @Authorized("*.MapSet.publish")
  @Post(uri = "/{mapSet}/entities/versions/{id}/retire")
  public HttpResponse<?> retireVersion(@PathVariable @ResourceId String mapSet, @PathVariable Long id) {
    mapSetEntityVersionService.retire(id);
    return HttpResponse.noContent();
  }

  @Authorized("*.MapSet.edit")
  @Post(uri = "/{mapSet}/versions/{version}/entity-versions/{entityVersionId}/membership")
  public HttpResponse<?> linkEntityVersion(@PathVariable @ResourceId String mapSet, @PathVariable String version, @PathVariable Long entityVersionId) {
    mapSetVersionService.linkEntityVersion(mapSet, version, entityVersionId);
    return HttpResponse.ok();
  }

  @Authorized("*.MapSet.edit")
  @Delete(uri = "/{mapSet}/versions/{version}/entity-versions/{entityVersionId}/membership")
  public HttpResponse<?> unlinkEntityVersion(@PathVariable @ResourceId String mapSet, @PathVariable String version, @PathVariable Long entityVersionId) {
    mapSetVersionService.unlinkEntityVersion(mapSet, version, entityVersionId);
    return HttpResponse.ok();
  }
}
