package com.kodality.termserver.mapset.association;

import com.kodality.termserver.commons.model.model.QueryResult;
import com.kodality.termserver.mapset.MapSetAssociation;
import com.kodality.termserver.mapset.MapSetAssociationQueryParams;
import com.kodality.termserver.mapset.MapSetEntityVersion;
import com.kodality.termserver.mapset.MapSetEntityVersionQueryParams;
import com.kodality.termserver.mapset.entity.MapSetEntityService;
import com.kodality.termserver.mapset.entity.MapSetEntityVersionService;
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

  public QueryResult<MapSetAssociation> query(MapSetAssociationQueryParams params) {
    QueryResult<MapSetAssociation> associations = repository.query(params);
    associations.getData().forEach(a -> decorate(a, params.getMapSetVersion()));
    return associations;
  }

  public Optional<MapSetAssociation> get(Long id) {
    return Optional.ofNullable(repository.load(id)).map(a -> decorate(a, null));
  }

  public Optional<MapSetAssociation> get(String mapSet, Long id) {
    return Optional.ofNullable(repository.load(mapSet, id)).map(a -> decorate(a, null));
  }

  public Optional<MapSetAssociation> get(String mapSet, String mapSetVersion, Long id) {
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
    return association;
  }


  @Transactional
  public MapSetAssociation save(MapSetAssociation association, String mapSet) {
    association.setMapSet(mapSet);

    Optional<MapSetAssociation> existingAssociation = get(mapSet, association.getId());
    if (existingAssociation.isPresent()) {
      association.setId(existingAssociation.get().getId());
    } else {
      mapSetEntityService.save(association);
      repository.save(association);
    }
    return association;
  }


}
