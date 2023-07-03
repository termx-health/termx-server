package com.kodality.termx.fileimporter.mapset.utils;

import com.kodality.termx.ts.Language;
import com.kodality.termx.ts.PublicationStatus;
import com.kodality.termx.ts.association.AssociationKind;
import com.kodality.termx.ts.association.AssociationType;
import com.kodality.termx.ts.codesystem.CodeSystemEntityVersion;
import com.kodality.termx.ts.mapset.MapSet;
import com.kodality.termx.ts.mapset.MapSetAssociation;
import com.kodality.termx.ts.mapset.MapSetEntityVersion;
import com.kodality.termx.ts.mapset.MapSetVersion;
import io.micronaut.core.util.CollectionUtils;
import java.util.ArrayList;
import java.util.List;

public class MapSetFileProcessingMapper {

  public MapSet mapMapSet(MapSetFileImportRequest req, List<MapSetFileImportRow> rows, MapSet existingMapSet) {
    MapSet ms = existingMapSet == null ? new MapSet() : existingMapSet;
    ms.setId(req.getMap().getId());
    ms.setUri(req.getMap().getUri() == null ? ms.getUri() : req.getMap().getUri());
    ms.setNames(req.getMap().getNames() == null ? ms.getNames() : req.getMap().getNames());
    ms.setSourceValueSet(req.getSourceValueSet() == null ? ms.getSourceValueSet() : req.getSourceValueSet());
    ms.setTargetValueSet(req.getSourceValueSet() == null ? ms.getTargetValueSet() : req.getTargetValueSet());
    ms.setVersions(List.of(mapVersion(req)));
    ms.setAssociations(mapAssociations(rows, req.getMap().getId()));
    return ms;
  }

  private static MapSetVersion mapVersion(MapSetFileImportRequest req) {
    MapSetVersion version = new MapSetVersion();
    version.setMapSet(req.getMap().getId());
    version.setVersion(req.getVersion().getVersion());
    version.setSupportedLanguages(List.of(Language.en));
    version.setStatus(PublicationStatus.draft);
    version.setReleaseDate(req.getVersion().getReleaseDate());
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
      association.setStatus(PublicationStatus.active);
      association.setSource(new CodeSystemEntityVersion().setCodeSystem(row.getSourceCodeSystem()).setCodeSystemVersion(row.getSourceVersion()).setCode(row.getSourceCode()));
      association.setTarget(new CodeSystemEntityVersion().setCodeSystem(row.getTargetCodeSystem()).setCodeSystemVersion(row.getTargetVersion()).setCode(row.getTargetCode()));
      association.setAssociationType(row.getEquivalence());
      association.setVersions(List.of(new MapSetEntityVersion().setStatus(PublicationStatus.draft).setDescription(row.getComment())));
      if (association.getSource() != null && association.getTarget() != null) {
        associations.add(association);
      }
    });
    return associations;
  }

  public List<AssociationType> mapAssociationTypes(List<MapSetFileImportRow> rows) {
    List<AssociationType> associationTypes = new ArrayList<>();
    if (CollectionUtils.isEmpty(rows)) {
      return associationTypes;
    }
    rows.forEach(row -> {
      if (row.getEquivalence() == null) {
        return;
      }
      associationTypes.add(new AssociationType(row.getEquivalence(), AssociationKind.conceptMapEquivalence, true));
    });
    return associationTypes;
  }
}
