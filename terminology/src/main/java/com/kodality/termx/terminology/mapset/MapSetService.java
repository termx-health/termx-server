package com.kodality.termx.terminology.mapset;

import com.kodality.commons.model.QueryResult;
import com.kodality.termx.auth.UserPermissionService;
import com.kodality.termx.terminology.mapset.association.MapSetAssociationService;
import com.kodality.termx.terminology.mapset.version.MapSetVersionService;
import com.kodality.termx.ts.mapset.MapSet;
import com.kodality.termx.ts.mapset.MapSetAssociation;
import com.kodality.termx.ts.mapset.MapSetQueryParams;
import com.kodality.termx.ts.mapset.MapSetTransactionRequest;
import com.kodality.termx.ts.mapset.MapSetVersion;
import com.kodality.termx.ts.mapset.MapSetVersionQueryParams;
import java.time.LocalDate;
import java.util.List;
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
    return load(id, false);
  }

  public Optional<MapSet> load(String id, boolean decorate) {
    return Optional.ofNullable(repository.load(id))
        .map(ms -> decorate ? decorate(ms, new MapSetQueryParams().setVersionsDecorated(true)) : ms);
  }

  @Transactional
  public void save(MapSet mapSet) {
    userPermissionService.checkPermitted(mapSet.getId(), "MapSet", "edit");
    repository.save(mapSet);
  }

  @Transactional
  public void save(MapSetTransactionRequest request) {
    MapSet mapSet = request.getMapSet();
    userPermissionService.checkPermitted(mapSet.getId(), "MapSet", "edit");
    repository.save(mapSet);

    if (request.getVersion() == null) {
      return;
    }

    MapSetVersion version = request.getVersion();
    version.setMapSet(mapSet.getId());
    version.setReleaseDate(version.getReleaseDate() == null ? LocalDate.now() : version.getReleaseDate());
    mapSetVersionService.save(version);

    List<MapSetAssociation> associations = request.getAssociations();
    if (associations != null) {
      mapSetAssociationService.batchUpsert(associations, version.getMapSet(), version.getVersion());
    }
  }

  public QueryResult<MapSet> query(MapSetQueryParams params) {
    QueryResult<MapSet> mapSets = repository.query(params);
    mapSets.getData().forEach(ms -> decorate(ms, params));
    return mapSets;
  }

  private MapSet decorate(MapSet mapSet, MapSetQueryParams params) {
    if (params.isVersionsDecorated()) {
      MapSetVersionQueryParams versionParams = new MapSetVersionQueryParams();
      versionParams.setMapSet(mapSet.getId());
      versionParams.setVersion(params.getVersionVersion());
      versionParams.all();
      mapSet.setVersions(mapSetVersionService.query(versionParams).getData());
    }
    return mapSet;
  }

  @Transactional
  public void cancel(String mapSet) {
    userPermissionService.checkPermitted(mapSet, "MapSet", "publish");
    repository.cancel(mapSet);
  }
}
