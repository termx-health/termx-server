package com.kodality.termserver.ts.mapset;

import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.auth.auth.UserPermissionService;
import com.kodality.termserver.mapset.MapSet;
import com.kodality.termserver.mapset.MapSetAssociationQueryParams;
import com.kodality.termserver.mapset.MapSetQueryParams;
import com.kodality.termserver.mapset.MapSetVersionQueryParams;
import com.kodality.termserver.ts.mapset.association.MapSetAssociationService;
import java.util.Optional;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class MapSetService {
  private final MapSetRepository repository;
  private final MapSetVersionService mapSetVersionService;
  private final MapSetAssociationService mapSetAssociationService;

  private final UserPermissionService userPermissionService;

  public Optional<MapSet> load(String id) {
    return Optional.ofNullable(repository.load(id));
  }

  @Transactional
  public void save(MapSet mapSet) {
    userPermissionService.checkPermitted(mapSet.getId(), "MapSet", "edit");
    repository.save(mapSet);
  }

  public QueryResult<MapSet> query(MapSetQueryParams params) {
    QueryResult<MapSet> mapSets = repository.query(params);
    mapSets.getData().forEach(ms -> decorate(ms, params));
    return mapSets;
  }

  private void decorate(MapSet mapSet, MapSetQueryParams params) {
    if (params.isAssociationsDecorated()) {
      MapSetAssociationQueryParams associationParams = new MapSetAssociationQueryParams();
      associationParams.setMapSet(mapSet.getId());
      associationParams.setMapSetVersion(params.getVersionVersion());
      associationParams.setSourceCode(params.getAssociationSourceCode());
      associationParams.setSourceSystemUri(params.getAssociationSourceSystemUri());
      associationParams.setSourceSystemVersion(params.getAssociationSourceSystemVersion());
      associationParams.setTargetSystem(params.getAssociationTargetSystem());
      associationParams.all();
      mapSet.setAssociations(mapSetAssociationService.query(associationParams).getData());
    }
    if (params.isVersionsDecorated()) {
      MapSetVersionQueryParams versionParams = new MapSetVersionQueryParams();
      versionParams.setMapSet(mapSet.getId());
      versionParams.setVersion(params.getVersionVersion());
      versionParams.all();
      mapSet.setVersions(mapSetVersionService.query(versionParams).getData());
    }
  }

  @Transactional
  public void cancel(String mapSet) {
    userPermissionService.checkPermitted(mapSet, "MapSet", "publish");
    repository.cancel(mapSet);
  }
}
