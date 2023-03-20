package com.kodality.termserver.ts.mapset;


import com.kodality.termserver.ApiError;
import com.kodality.termserver.ts.PublicationStatus;
import com.kodality.termserver.ts.association.AssociationType;
import com.kodality.termserver.ts.mapset.MapSet;
import com.kodality.termserver.ts.mapset.MapSetAssociation;
import com.kodality.termserver.ts.mapset.MapSetVersion;
import com.kodality.termserver.ts.association.AssociationTypeService;
import com.kodality.termserver.ts.mapset.MapSetService;
import com.kodality.termserver.ts.mapset.MapSetVersionService;
import com.kodality.termserver.ts.mapset.association.MapSetAssociationService;
import com.kodality.termserver.ts.mapset.entity.MapSetEntityVersionService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
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
  private final AssociationTypeService associationTypeService;
  private final MapSetAssociationService mapSetAssociationService;
  private final MapSetEntityVersionService mapSetEntityVersionService;

  @Transactional
  public void importMapSet(MapSet mapSet, List<AssociationType> associationTypes, boolean activateVersion) {
    associationTypes.forEach(associationTypeService::save);

    saveMapSet(mapSet);
    MapSetVersion mapSetVersion = mapSet.getVersions().get(0);
    saveMapSetVersion(mapSetVersion);

    saveAssociations(mapSet.getAssociations(), mapSetVersion);

    if (activateVersion) {
      mapSetVersionService.activate(mapSet.getId(), mapSetVersion.getVersion());
    }
  }

  private void saveMapSet(MapSet mapSet) {
    log.info("Saving map set");
    Optional<MapSet> existingMapSet = mapSetService.load(mapSet.getId());
    if (existingMapSet.isEmpty()) {
      log.info("Map set {} does not exist, creating new", mapSet.getId());
      mapSetService.save(mapSet);
    }
  }

  private void saveMapSetVersion(MapSetVersion mapSetVersion) {
    Optional<MapSetVersion> existingVersion = mapSetVersionService.load(mapSetVersion.getMapSet(), mapSetVersion.getVersion());
    if (existingVersion.isPresent() && !existingVersion.get().getStatus().equals(PublicationStatus.draft)) {
      throw ApiError.TE104.toApiException(Map.of("version", mapSetVersion.getVersion()));
    }
    log.info("Saving map set version {}", mapSetVersion.getVersion());
    mapSetVersion.setId(existingVersion.map(MapSetVersion::getId).orElse(null));
    mapSetVersionService.save(mapSetVersion);
  }

  private void saveAssociations(List<MapSetAssociation> associations, MapSetVersion version) {
    log.info("Creating '{}' associations", associations.size());
    associations.forEach(association -> {
      mapSetAssociationService.save(association, version.getMapSet());

      mapSetEntityVersionService.save(association.getVersions().get(0), association.getId());
      mapSetEntityVersionService.activate(association.getVersions().get(0).getId());
    });
    log.info("Associations created");

    log.info("Linking map set version and association versions");
    mapSetVersionService.saveEntityVersions(version.getId(), associations.stream().map(c -> c.getVersions().get(0)).collect(Collectors.toList()));
  }
}
