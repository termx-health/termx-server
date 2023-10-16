package com.kodality.termx.terminology.fhir.conceptmap;

import com.kodality.termx.core.http.BinaryHttpClient;
import com.kodality.termx.terminology.terminology.mapset.MapSetImportService;
import com.kodality.termx.ts.PublicationStatus;
import com.kodality.termx.ts.association.AssociationType;
import com.kodality.termx.ts.mapset.MapSet;
import com.kodality.termx.ts.mapset.MapSetImportAction;
import com.kodality.zmei.fhir.FhirMapper;
import com.kodality.zmei.fhir.resource.Resource;
import com.kodality.zmei.fhir.resource.other.Bundle;
import com.kodality.zmei.fhir.resource.terminology.ConceptMap;
import jakarta.inject.Singleton;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class ConceptMapFhirImportService {
  private final MapSetImportService mapSetImportService;
  private final ConceptMapFhirMapper mapper;
  private final BinaryHttpClient client = new BinaryHttpClient();

  @Transactional
  public void importMapSet(ConceptMap fhirConceptMap, MapSetImportAction action) {
    List<AssociationType> associationTypes = mapper.fromFhirAssociationTypes(fhirConceptMap);
    MapSet mapSet = mapper.fromFhir(fhirConceptMap);

    action.setActivate(PublicationStatus.active.equals(fhirConceptMap.getStatus()));
    mapSetImportService.importMapSet(mapSet, associationTypes, action);
  }

  @Transactional
  public void importMapSetFromUrl(String url, String mapSetId, MapSetImportAction action) {
    String resource = getResource(url);
    importMapSet(resource, mapSetId, action);
  }

  public void importMapSet(String resource, String mapSetId, MapSetImportAction action) {
    Resource res = FhirMapper.fromJson(resource, Resource.class);
    if ("Bundle".equals(res.getResourceType())) {
      Bundle bundle = FhirMapper.fromJson(resource, Bundle.class);
      bundle.getEntry().forEach(e -> importMapSet((ConceptMap) e.getResource(), action));
    } else {
      ConceptMap conceptMap = FhirMapper.fromJson(resource, ConceptMap.class);
      conceptMap.setId(mapSetId);
      importMapSet(conceptMap, action);
    }
  }

  private String getResource(String url) {
    log.info("Loading fhir map set from {}", url);
    return new String(client.GET(url).body(), StandardCharsets.UTF_8);
  }

}
