package com.kodality.termx.terminology.mapset.association;

import com.kodality.commons.model.QueryResult;
import com.kodality.termx.auth.UserPermissionService;
import com.kodality.termx.terminology.mapset.statistics.MapSetStatisticsService;
import com.kodality.termx.terminology.mapset.version.MapSetVersionRepository;
import com.kodality.termx.ts.PublicationStatus;
import com.kodality.termx.ts.mapset.MapSetAssociation;
import com.kodality.termx.ts.mapset.MapSetAssociationQueryParams;
import com.kodality.termx.ts.mapset.MapSetVersion;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class MapSetAssociationService {
  private final MapSetAssociationRepository repository;
  private final MapSetStatisticsService mapSetStatisticsService;
  private final MapSetVersionRepository mapSetVersionRepository;

  private final UserPermissionService userPermissionService;

  public QueryResult<MapSetAssociation> query(MapSetAssociationQueryParams params) {
    return repository.query(params);
  }

  public Optional<MapSetAssociation> load(Long id) {
    return Optional.ofNullable(repository.load(id));
  }

  public Optional<MapSetAssociation> load(String mapSet, Long id) {
    return Optional.ofNullable(repository.load(mapSet, id));
  }

  public Optional<MapSetAssociation> load(String mapSet, String mapSetVersion, Long id) {
    return query(new MapSetAssociationQueryParams()
        .setMapSet(mapSet)
        .setMapSetVersion(mapSetVersion)
        .setId(id)).findFirst();
  }


  @Transactional
  public MapSetAssociation save(MapSetAssociation association, String mapSet, String version) {
    userPermissionService.checkPermitted(mapSet, "MapSet", "edit");

    MapSetVersion msv = mapSetVersionRepository.load(mapSet, version);
    association.setMapSet(mapSet);
    association.setMapSetVersion(msv);
    repository.save(association);
    if (PublicationStatus.draft.equals(msv.getStatus())) {
      mapSetStatisticsService.calculate(msv.getMapSet(), msv.getVersion());
    }
    return association;
  }

  @Transactional
  public void batchSave(List<MapSetAssociation> associations, String mapSet, String version) {
    userPermissionService.checkPermitted(mapSet, "MapSet", "edit");

    MapSetVersion msv = mapSetVersionRepository.load(mapSet, version);
    repository.retain(mapSet, msv.getId(), associations.stream().map(MapSetAssociation::getId).filter(Objects::nonNull).toList());
    repository.batchUpsert(associations, mapSet, msv.getId());
    if (PublicationStatus.draft.equals(msv.getStatus())) {
      mapSetStatisticsService.calculate(msv.getMapSet(), msv.getVersion());
    }
  }

  @Transactional
  public void batchUpsert(List<MapSetAssociation> associations, String mapSet, String version) {
    userPermissionService.checkPermitted(mapSet, "MapSet", "edit");

    MapSetVersion msv = mapSetVersionRepository.load(mapSet, version);
    repository.batchUpsert(associations, mapSet, msv.getId());
    if (PublicationStatus.draft.equals(msv.getStatus())) {
      mapSetStatisticsService.calculate(msv.getMapSet(), msv.getVersion());
    }
  }

  @Transactional
  public void verify(List<Long> verifiedIds, List<Long> unVerifiedIds, String mapSet) {
    userPermissionService.checkPermitted(mapSet, "MapSet", "edit");
    repository.verify(verifiedIds, true);
    repository.verify(unVerifiedIds, false);
  }

  @Transactional
  public void cancel(List<Long> ids, String mapSet, String version) {
    userPermissionService.checkPermitted(mapSet, "MapSet", "edit");

    MapSetVersion msv = mapSetVersionRepository.load(mapSet, version);
    repository.cancel(ids);
    if (PublicationStatus.draft.equals(msv.getStatus())) {
      mapSetStatisticsService.calculate(msv.getMapSet(), msv.getVersion());
    }
  }
}
