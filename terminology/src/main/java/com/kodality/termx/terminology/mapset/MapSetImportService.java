package com.kodality.termx.terminology.mapset;


import com.kodality.termx.ApiError;
import com.kodality.termx.auth.UserPermissionService;
import com.kodality.termx.terminology.association.AssociationTypeService;
import com.kodality.termx.terminology.mapset.association.MapSetAssociationService;
import com.kodality.termx.terminology.mapset.version.MapSetVersionService;
import com.kodality.termx.ts.PublicationStatus;
import com.kodality.termx.ts.association.AssociationType;
import com.kodality.termx.ts.mapset.MapSet;
import com.kodality.termx.ts.mapset.MapSetAssociation;
import com.kodality.termx.ts.mapset.MapSetAssociationQueryParams;
import com.kodality.termx.ts.mapset.MapSetImportAction;
import com.kodality.termx.ts.mapset.MapSetVersion;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class MapSetImportService {
  private final MapSetService mapSetService;
  private final MapSetVersionService mapSetVersionService;
  private final MapSetAssociationService mapSetAssociationService;
  private final AssociationTypeService associationTypeService;

  private final UserPermissionService userPermissionService;

  @Transactional
  public void importMapSet(MapSet mapSet, List<AssociationType> associationTypes, MapSetImportAction action) {
    userPermissionService.checkPermitted(mapSet.getId(), "MapSet", "edit");

    long start = System.currentTimeMillis();
    log.info("IMPORT STARTED : map set - {}", mapSet.getId());

    associationTypeService.createIfNotExist(associationTypes);

    saveMapSet(mapSet);
    MapSetVersion mapSetVersion = mapSet.getVersions().get(0);
    saveMapSetVersion(mapSetVersion, action.isCleanRun());

    saveAssociations(mapSetVersion.getAssociations(), mapSetVersion, action.isCleanAssociationRun());

    if (action.isActivate()) {
      mapSetVersionService.activate(mapSet.getId(), mapSetVersion.getVersion());
    }

    log.info("IMPORT FINISHED (" + (System.currentTimeMillis() - start) / 1000 + " sec)");
  }

  private void saveMapSet(MapSet mapSet) {
    log.info("Saving map set");

    Optional<MapSet> existingMapSet = mapSetService.load(mapSet.getId());
    if (existingMapSet.isEmpty()) {
      log.info("Map set {} does not exist, creating new", mapSet.getId());
      mapSetService.save(mapSet);
    }
  }

  private void saveMapSetVersion(MapSetVersion mapSetVersion, boolean cleanRun) {
    Optional<MapSetVersion> existingVersion = mapSetVersionService.load(mapSetVersion.getMapSet(), mapSetVersion.getVersion());

    if (cleanRun && existingVersion.isPresent()) {
      log.info("Cancelling existing map set version {}", mapSetVersion.getVersion());
      mapSetVersionService.cancel(existingVersion.get().getId(), existingVersion.get().getMapSet());
    } else if (existingVersion.isPresent() && !existingVersion.get().getStatus().equals(PublicationStatus.draft)) {
      throw ApiError.TE104.toApiException(Map.of("version", mapSetVersion.getVersion()));
    } else if (existingVersion.isPresent() && existingVersion.get().getStatus().equals(PublicationStatus.draft) && mapSetVersion.getId() == null) {
      mapSetVersion.setId(existingVersion.get().getId());
    }
    log.info("Saving map set version {}", mapSetVersion.getVersion());
    mapSetVersionService.save(mapSetVersion);
  }

  private void saveAssociations(List<MapSetAssociation> associations, MapSetVersion version, boolean cleanRun) {
    log.info("Creating '{}' associations", associations.size());
    long start = System.currentTimeMillis();
    if (!cleanRun) {
      mapSetAssociationService.batchUpsert(associations, version.getMapSet(), version.getVersion());
    } else {
      List<MapSetAssociation> existing = new ArrayList<>(mapSetAssociationService.query(new MapSetAssociationQueryParams().setMapSetVersionId(version.getId()).all()).getData());
      List<String> existingKeys = existing.stream().map(this::getAssociationGroupingKey).toList();
      existing.addAll(associations.stream().filter(a -> !existingKeys.contains(getAssociationGroupingKey(a))).toList());
      mapSetAssociationService.batchSave(existing, version.getMapSet(), version.getVersion());
    }
    log.info("Associations created (" + (System.currentTimeMillis() - start) / 1000 + " sec)");
  }

  private String getAssociationGroupingKey(MapSetAssociation a) {
    return a.getSource().getCode() + a.getSource().getCodeSystem() +
        ( a.getTarget() != null ? a.getTarget().getCode() + a.getTarget().getCodeSystem() : a.isNoMap());
  }

}
