package com.kodality.termserver.ts.mapset.association;

import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.auth.auth.UserPermissionService;
import com.kodality.termserver.mapset.MapSetAssociation;
import com.kodality.termserver.mapset.MapSetAssociationQueryParams;
import com.kodality.termserver.mapset.MapSetEntityVersion;
import com.kodality.termserver.mapset.MapSetEntityVersionQueryParams;
import com.kodality.termserver.ts.codesystem.entity.CodeSystemEntityVersionService;
import com.kodality.termserver.ts.mapset.entity.MapSetEntityService;
import com.kodality.termserver.ts.mapset.entity.MapSetEntityVersionService;
import java.util.List;
import java.util.Optional;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class MapSetAssociationService {
  private final MapSetAssociationRepository repository;
  private final MapSetEntityService mapSetEntityService;
  private final MapSetEntityVersionService mapSetEntityVersionService;
  private final CodeSystemEntityVersionService codeSystemEntityVersionService;

  private final UserPermissionService userPermissionService;

  public QueryResult<MapSetAssociation> query(MapSetAssociationQueryParams params) {
    QueryResult<MapSetAssociation> associations = repository.query(params);
    associations.getData().forEach(a -> decorate(a, params.getMapSetVersion()));
    return associations;
  }

  public Optional<MapSetAssociation> load(Long id) {
    return Optional.ofNullable(repository.load(id)).map(a -> decorate(a, null));
  }

  public Optional<MapSetAssociation> load(String mapSet, Long id) {
    return Optional.ofNullable(repository.load(mapSet, id)).map(a -> decorate(a, null));
  }

  public Optional<MapSetAssociation> load(String mapSet, String mapSetVersion, Long id) {
    return query(new MapSetAssociationQueryParams()
        .setMapSet(mapSet)
        .setMapSetVersion(mapSetVersion)
        .setId(id)).findFirst().map(a -> decorate(a, mapSetVersion));
  }

  private MapSetAssociation decorate(MapSetAssociation association, String mapSetVersion) {
    List<MapSetEntityVersion> versions = mapSetEntityVersionService.query(new MapSetEntityVersionQueryParams()
        .setMapSetEntityId(association.getId())
        .setMapSetVersion(mapSetVersion)
        .setMapSet(association.getMapSet())).getData();
    association.setVersions(versions);
    association.setSource(codeSystemEntityVersionService.load(association.getSource().getId()));
    association.setTarget(codeSystemEntityVersionService.load(association.getTarget().getId()));
    return association;
  }


  @Transactional
  public MapSetAssociation save(MapSetAssociation association, String mapSet) {
    userPermissionService.checkPermitted(mapSet, "MapSet", "edit");

    association.setMapSet(mapSet);
    mapSetEntityService.save(association);
    repository.save(association);
    mapSetEntityVersionService.save(association.getVersions(), association.getId());
    return association;
  }


}
