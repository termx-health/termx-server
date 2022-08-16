package com.kodality.termserver.fhir.conceptmap;

import com.kodality.commons.model.LocalizedName;
import com.kodality.termserver.Language;
import com.kodality.termserver.PublicationStatus;
import com.kodality.termserver.association.AssociationKind;
import com.kodality.termserver.association.AssociationType;
import com.kodality.termserver.codesystem.CodeSystemEntityVersion;
import com.kodality.termserver.mapset.MapSet;
import com.kodality.termserver.mapset.MapSetAssociation;
import com.kodality.termserver.mapset.MapSetEntityVersion;
import com.kodality.termserver.mapset.MapSetVersion;
import com.kodality.zmei.fhir.resource.terminology.ConceptMap;
import io.micronaut.core.util.CollectionUtils;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ConceptMapFhirImportMapper {

  public static MapSet mapMapSet(ConceptMap conceptMap) {
    MapSet ms = new MapSet();
    ms.setId(conceptMap.getId());
    ms.setUri(conceptMap.getUrl());
    ms.setNames(new LocalizedName(Map.of(Language.en, conceptMap.getName())));
    ms.setDescription(conceptMap.getDescription());
    ms.setVersions(List.of(mapVersion(conceptMap)));
    ms.setAssociations(mapAssociations(conceptMap));
    ms.setSourceValueSet(conceptMap.getSourceUri() == null ? conceptMap.getSourceCanonical() : conceptMap.getSourceUri());
    ms.setTargetValueSet(conceptMap.getTargetUri() == null ? conceptMap.getTargetCanonical() : conceptMap.getTargetUri());
    return ms;
  }

  private static MapSetVersion mapVersion(ConceptMap conceptMap) {
    MapSetVersion version = new MapSetVersion();
    version.setMapSet(conceptMap.getId());
    version.setVersion(conceptMap.getVersion());
    version.setSupportedLanguages(List.of(Language.en));
    version.setStatus(PublicationStatus.draft);
    version.setReleaseDate(LocalDate.from(conceptMap.getDate()));
    version.setSource(conceptMap.getPublisher());
    return version;
  }

  private static List<MapSetAssociation> mapAssociations(ConceptMap conceptMap) {
    List<MapSetAssociation> associations = new ArrayList<>();
    if (CollectionUtils.isEmpty(conceptMap.getGroup())) {
      return associations;
    }
    conceptMap.getGroup().forEach(g -> {
      g.getElement().forEach(element -> {
        element.getTarget().forEach(target -> {
          MapSetAssociation association = new MapSetAssociation();
          association.setMapSet(conceptMap.getId());
          association.setStatus(PublicationStatus.active);
          association.setSource(new CodeSystemEntityVersion().setCodeSystem(g.getSource()).setCodeSystemVersion(g.getSourceVersion()).setCode(element.getCode()));
          association.setTarget(new CodeSystemEntityVersion().setCodeSystem(g.getTarget()).setCodeSystemVersion(g.getTargetVersion()).setCode(target.getCode()));
          association.setAssociationType(target.getEquivalence());
          association.setVersions(List.of(new MapSetEntityVersion().setStatus(PublicationStatus.draft)));
          if (association.getSource() != null && association.getTarget() != null) {
            associations.add(association);
          }
        });
      });
    });
    return associations;
  }

  public static List<AssociationType> mapAssociationTypes(ConceptMap conceptMap) {
    List<AssociationType> associationTypes = new ArrayList<>();
    if (CollectionUtils.isEmpty(conceptMap.getGroup())) {
      return associationTypes;
    }
    conceptMap.getGroup().forEach(g -> g.getElement().forEach(element -> element.getTarget().forEach(target -> {
      if (target.getCode() == null) {
        return;
      }
      associationTypes.add(new AssociationType(target.getEquivalence(), AssociationKind.conceptMapEquivalence, true));
    })));
    return associationTypes;
  }

}
