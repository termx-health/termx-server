package com.kodality.termserver.terminology.mapset;

import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.auth.UserPermissionService;
import com.kodality.termserver.terminology.mapset.association.MapSetAssociationService;
import com.kodality.termserver.ts.mapset.MapSet;
import com.kodality.termserver.ts.mapset.MapSetAssociation;
import com.kodality.termserver.ts.mapset.MapSetAssociationQueryParams;
import com.kodality.termserver.ts.mapset.MapSetQueryParams;
import com.kodality.termserver.ts.mapset.MapSetTransactionRequest;
import com.kodality.termserver.ts.mapset.MapSetVersion;
import com.kodality.termserver.ts.mapset.MapSetVersionQueryParams;
import io.micronaut.core.util.CollectionUtils;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
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
        .map(ms -> decorate ? decorate(ms, new MapSetQueryParams().setAssociationsDecorated(true).setVersionsDecorated(true)) : ms);
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

    MapSetVersion version = request.getVersion();
    version.setMapSet(mapSet.getId());
    version.setReleaseDate(version.getReleaseDate() == null ? LocalDate.now() : version.getReleaseDate());
    mapSetVersionService.save(version);

    List<MapSetAssociation> associations = request.getAssociations();
    if (CollectionUtils.isNotEmpty(request.getAssociations())) {
      associations.forEach(association -> mapSetAssociationService.save(association, version.getMapSet()));
      mapSetVersionService.saveEntityVersions(version.getId(), associations.stream().map(a -> a.getVersions().get(0)).collect(Collectors.toList()));
    }
  }

  public QueryResult<MapSet> query(MapSetQueryParams params) {
    QueryResult<MapSet> mapSets = repository.query(params);
    mapSets.getData().forEach(ms -> decorate(ms, params));
    return mapSets;
  }

  private MapSet decorate(MapSet mapSet, MapSetQueryParams params) {
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
    return mapSet;
  }

  @Transactional
  public void cancel(String mapSet) {
    userPermissionService.checkPermitted(mapSet, "MapSet", "publish");
    repository.cancel(mapSet);
  }
}
