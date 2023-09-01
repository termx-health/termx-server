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

  public MapSet mapMapSet(MapSetFileImportRequest req, List<MapSetFileImportRow> rows, MapSet existingMapSet) {
    MapSet ms = existingMapSet == null ? new MapSet() : existingMapSet;
    ms.setId(req.getMap().getId());
    ms.setUri(req.getMap().getUri() == null ? ms.getUri() : req.getMap().getUri());
    ms.setTitle(req.getMap().getNames() == null ? ms.getTitle() : req.getMap().getNames());
    ms.setVersions(List.of(mapVersion(req, rows)));
    return ms;
  }

  private static MapSetVersion mapVersion(MapSetFileImportRequest req, List<MapSetFileImportRow> rows) {
    MapSetVersionScope scope = new MapSetVersionScope();
    scope.setSourceType("value-set");
    scope.setSourceValueSet(new MapSetResourceReference().setId(req.getSourceValueSet()));
    scope.setTargetType("value-set");
    scope.setTargetValueSet(new MapSetResourceReference().setId(req.getTargetValueSet()));

    MapSetVersion version = new MapSetVersion();
    version.setMapSet(req.getMap().getId());
    version.setVersion(req.getVersion().getVersion());
    version.setStatus(PublicationStatus.draft);
    version.setReleaseDate(req.getVersion().getReleaseDate());
    version.setPreferredLanguage(Language.en);
    version.setScope(scope);
    version.setAssociations(mapAssociations(rows, req.getMap().getId()));
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
