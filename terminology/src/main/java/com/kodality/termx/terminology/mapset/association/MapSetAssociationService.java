package com.kodality.termx.terminology.mapset.association;

import com.kodality.commons.model.QueryResult;
import com.kodality.termx.terminology.mapset.association.propertyvalue.MapSetPropertyValueService;
import com.kodality.termx.terminology.mapset.statistics.MapSetStatisticsService;
import com.kodality.termx.terminology.mapset.version.MapSetVersionRepository;
import com.kodality.termx.ts.PublicationStatus;
import com.kodality.termx.ts.mapset.MapSetAssociation;
import com.kodality.termx.ts.mapset.MapSetAssociationQueryParams;
import com.kodality.termx.ts.mapset.MapSetPropertyValue;
import com.kodality.termx.ts.mapset.MapSetVersion;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class MapSetAssociationService {
  private final MapSetAssociationRepository repository;
  private final MapSetStatisticsService mapSetStatisticsService;
  private final MapSetVersionRepository mapSetVersionRepository;
  private final MapSetPropertyValueService mapSetPropertyValueService;

  public QueryResult<MapSetAssociation> query(MapSetAssociationQueryParams params) {
    return repository.query(params);
  }

  @Transactional
  public MapSetAssociation save(MapSetAssociation association, String mapSet, String version) {
    MapSetVersion msv = mapSetVersionRepository.load(mapSet, version);
    association.setMapSet(mapSet);
    association.setMapSetVersion(msv);
    repository.save(association);
    mapSetPropertyValueService.save(association.getPropertyValues(), association.getId(), mapSet);

    if (PublicationStatus.draft.equals(msv.getStatus())) {
      mapSetStatisticsService.calculate(msv.getMapSet(), msv.getVersion());
    }
    return association;
  }

  @Transactional
  public void batchSave(List<MapSetAssociation> associations, String mapSet, String version) {
    MapSetVersion msv = mapSetVersionRepository.load(mapSet, version);
    repository.retain(mapSet, msv.getId(), associations.stream().map(MapSetAssociation::getId).filter(Objects::nonNull).toList());
    batchUpsert(associations, mapSet, version);
  }

  @Transactional
  public void batchUpsert(List<MapSetAssociation> associations, String mapSet, String version) {
    MapSetVersion msv = mapSetVersionRepository.load(mapSet, version);
    repository.batchUpsert(associations, mapSet, msv.getId());

    Map<Long, List<MapSetPropertyValue>> propertyValues = associations.stream().collect(Collectors.toMap(MapSetAssociation::getId, MapSetAssociation::getPropertyValues));
    mapSetPropertyValueService.batchUpsert(propertyValues, mapSet);

    if (PublicationStatus.draft.equals(msv.getStatus())) {
      mapSetStatisticsService.calculate(msv.getMapSet(), msv.getVersion());
    }
  }

  @Transactional
  public void verify(List<Long> verifiedIds, List<Long> unVerifiedIds, String mapSet) {
    repository.verify(verifiedIds, true);
    repository.verify(unVerifiedIds, false);
  }

  @Transactional
  public void cancel(List<Long> ids, String mapSet, String version) {
    MapSetVersion msv = mapSetVersionRepository.load(mapSet, version);
    repository.cancel(ids);

    ids.forEach(id -> mapSetPropertyValueService.save(List.of(), id, mapSet));

    if (PublicationStatus.draft.equals(msv.getStatus())) {
      mapSetStatisticsService.calculate(msv.getMapSet(), msv.getVersion());
    }
  }
}
