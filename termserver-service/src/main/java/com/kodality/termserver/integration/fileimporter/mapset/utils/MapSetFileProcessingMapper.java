package com.kodality.termserver.integration.fileimporter.mapset.utils;

import com.kodality.termserver.Language;
import com.kodality.termserver.PublicationStatus;
import com.kodality.termserver.association.AssociationKind;
import com.kodality.termserver.association.AssociationType;
import com.kodality.termserver.codesystem.CodeSystemEntityVersion;
import com.kodality.termserver.mapset.MapSet;
import com.kodality.termserver.mapset.MapSetAssociation;
import com.kodality.termserver.mapset.MapSetEntityVersion;
import com.kodality.termserver.mapset.MapSetVersion;
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
      AssociationType associationType = new AssociationType();
      associationType.setCode(row.getEquivalence());
      associationType.setDirected(true);
      associationType.setAssociationKind(AssociationKind.conceptMapEquivalence);
      associationTypes.add(associationType);
    });
    return associationTypes;
  }
}
