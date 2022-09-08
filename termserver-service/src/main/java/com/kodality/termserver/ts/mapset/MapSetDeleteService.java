package com.kodality.termserver.ts.mapset;

import com.kodality.termserver.mapset.MapSetAssociation;
import com.kodality.termserver.mapset.MapSetAssociationQueryParams;
import com.kodality.termserver.mapset.MapSetVersion;
import com.kodality.termserver.mapset.MapSetVersionQueryParams;
import com.kodality.termserver.ts.mapset.association.MapSetAssociationService;
import com.kodality.termserver.ts.mapset.entity.MapSetEntityVersionService;
import java.util.List;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class MapSetDeleteService {
  private final MapSetService mapSetService;
  private final MapSetVersionService mapSetVersionService;
  private final MapSetAssociationService mapSetAssociationService;
  private final MapSetEntityVersionService mapSetEntityVersionService;

  @Transactional
  public void deleteMapSet(String mapSet) {
    deleteMapSetAssociations(mapSet);
    deleteMapSetVersions(mapSet);
    mapSetService.cancel(mapSet);
  }

  private void deleteMapSetAssociations(String mapSet) {
    MapSetAssociationQueryParams mapSetAssociationParams = new MapSetAssociationQueryParams().setMapSet(mapSet);
    mapSetAssociationParams.all();
    List<MapSetAssociation> mapSetAssociations = mapSetAssociationService.query(mapSetAssociationParams).getData();
    mapSetAssociations.forEach(a -> {
      a.getVersions().forEach(v -> mapSetEntityVersionService.cancel(v.getId(), mapSet));
      mapSetAssociationService.cancel(a.getId(), mapSet);
    });
  }

  private void deleteMapSetVersions(String mapSet) {
    MapSetVersionQueryParams mapSetVersionParams = new MapSetVersionQueryParams().setMapSet(mapSet);
    mapSetVersionParams.all();
    List<MapSetVersion> mapSetVersions = mapSetVersionService.query(mapSetVersionParams).getData();
    mapSetVersions.forEach(v -> mapSetVersionService.cancel(v.getId(), mapSet));
  }
}
