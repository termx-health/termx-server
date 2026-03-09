package com.kodality.termx.terminology.terminology.mapset;

import com.kodality.termx.terminology.ApiError;
import com.kodality.termx.terminology.terminology.mapset.association.MapSetAssociationService;
import com.kodality.termx.terminology.terminology.mapset.version.MapSetVersionService;
import com.kodality.termx.ts.PublicationStatus;
import com.kodality.termx.ts.mapset.MapSetAssociation;
import com.kodality.termx.ts.mapset.MapSetAssociationQueryParams;
import com.kodality.termx.ts.mapset.MapSetVersion;
import java.util.List;
import java.util.Map;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class MapSetDuplicateService {
  private final MapSetVersionService mapSetVersionService;
  private final MapSetAssociationService mapSetAssociationService;

  @Transactional
  public void duplicateMapSetVersion(String targetVersionVersion, String targetMapSet, String sourceVersionVersion, String sourceMapSet) {
    MapSetVersion version = mapSetVersionService.load(sourceMapSet, sourceVersionVersion).orElseThrow(() ->
        ApiError.TE401.toApiException(Map.of("version", sourceVersionVersion, "mapSet", sourceMapSet)));
    Long sourceVersionId = version.getId();
    version.setId(null);
    version.setVersion(targetVersionVersion);
    version.setMapSet(targetMapSet);
    version.setStatus(PublicationStatus.draft);
    version.setCreated(null);
    if (version.getStatistics() != null) {
      version.getStatistics().setId(null);
    }
    mapSetVersionService.save(version);
    List<MapSetAssociation> associations = mapSetAssociationService.query(new MapSetAssociationQueryParams()
        .setMapSet(sourceMapSet)
        .setMapSetVersionId(sourceVersionId)
        .all()).getData();
    if (CollectionUtils.isNotEmpty(associations)) {
      associations.forEach(a -> {
        a.setId(null);
        if (CollectionUtils.isNotEmpty(a.getPropertyValues())) {
          a.getPropertyValues().forEach(pv -> pv.setId(null));
        }
      });
      mapSetAssociationService.batchUpsert(associations, targetMapSet, targetVersionVersion);
    }
  }
}
