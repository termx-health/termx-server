package com.kodality.termx.fileimporter.mapset.utils;

import com.kodality.termx.ts.Language;
import com.kodality.termx.ts.PublicationStatus;
import com.kodality.termx.ts.association.AssociationKind;
import com.kodality.termx.ts.association.AssociationType;
import com.kodality.termx.ts.mapset.MapSet;
import com.kodality.termx.ts.mapset.MapSetAssociation;
import com.kodality.termx.ts.mapset.MapSetAssociation.MapSetAssociationEntity;
import com.kodality.termx.ts.mapset.MapSetVersion;
import com.kodality.termx.ts.mapset.MapSetVersion.MapSetResourceReference;
import com.kodality.termx.ts.mapset.MapSetVersion.MapSetVersionScope;
import io.micronaut.core.util.CollectionUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class MapSetFileProcessingMapper {

  public MapSet mapMapSet(MapSetFileImportRequest req, List<MapSetFileImportRow> rows, MapSet existingMapSet, MapSetVersion existingMapSetVersion) {
    MapSet ms = existingMapSet == null ? new MapSet() : existingMapSet;
    ms.setId(req.getMapSet().getId());
    ms.setUri(req.getMapSet().getUri() == null ? ms.getUri() : req.getMapSet().getUri());
    ms.setTitle(req.getMapSet().getTitle() == null ? ms.getTitle() : req.getMapSet().getTitle());
    ms.setDescription(req.getMapSet().getDescription() == null ? ms.getDescription() : req.getMapSet().getDescription());
    ms.setVersions(List.of(mapVersion(req, rows, existingMapSetVersion)));
    return ms;
  }

  private static MapSetVersion mapVersion(MapSetFileImportRequest req, List<MapSetFileImportRow> rows, MapSetVersion existingMapSetVersion) {
    MapSetVersion version = new MapSetVersion();
    version.setMapSet(req.getMapSet().getId());
    version.setVersion(req.getMapSetVersion().getVersion());
    version.setStatus(PublicationStatus.draft);
    version.setReleaseDate(req.getMapSetVersion().getReleaseDate());
    version.setPreferredLanguage(Language.en);
    version.setScope(req.getMapSetVersion().getScope());

    if (existingMapSetVersion != null) {
      version = existingMapSetVersion;
    }
    version.setAssociations(mapAssociations(rows, req.getMapSet().getId()));
    return version;
  }

  private static List<MapSetAssociation> mapAssociations(List<MapSetFileImportRow> rows, String mapSetId) {
    List<MapSetAssociation> associations = new ArrayList<>();
    if (CollectionUtils.isEmpty(rows)) {
      return associations;
    }
    rows.forEach(row -> {
      MapSetAssociation association = new MapSetAssociation();
      association.setMapSet(mapSetId);
      association.setSource(new MapSetAssociationEntity().setCode(row.getSourceCode()).setCodeSystem(row.getSourceCodeSystem()));
      association.setTarget(new MapSetAssociationEntity().setCode(row.getTargetCode()).setCodeSystem(row.getTargetCodeSystem()));
      association.setRelationship(row.getEquivalence());
      if (row.getSourceCode() != null) {
        associations.add(association);
      }
    });
    return associations;
  }

  public List<AssociationType> mapAssociationTypes(List<MapSetFileImportRow> rows) {
    if (rows == null) {
      return List.of();
    }
    return rows.stream().map(MapSetFileImportRow::getEquivalence).filter(Objects::nonNull).distinct()
        .map(e -> new AssociationType(e, AssociationKind.conceptMapEquivalence, true)).toList();
  }
}
