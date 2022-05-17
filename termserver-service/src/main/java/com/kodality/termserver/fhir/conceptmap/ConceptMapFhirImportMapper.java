package com.kodality.termserver.fhir.conceptmap;

import com.kodality.commons.model.LocalizedName;
import com.kodality.termserver.Language;
import com.kodality.termserver.PublicationStatus;
import com.kodality.termserver.mapset.MapSet;
import com.kodality.termserver.mapset.MapSetVersion;
import com.kodality.zmei.fhir.resource.terminology.ConceptMap;
import java.time.LocalDate;
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

}
