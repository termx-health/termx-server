package com.kodality.termx.fhir.conceptmap;

import com.kodality.termx.ApiError;
import com.kodality.termx.http.BinaryHttpClient;
import com.kodality.termx.terminology.mapset.MapSetImportService;
import com.kodality.termx.ts.association.AssociationType;
import com.kodality.termx.ts.mapset.MapSet;
import com.kodality.zmei.fhir.FhirMapper;
import com.kodality.zmei.fhir.resource.ResourceType;
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
  private final ConceptMapFhirImportMapper mapper;
  private final MapSetImportService mapSetImportService;
  private final BinaryHttpClient client = new BinaryHttpClient();

  @Transactional
  public void importMapSet(String url, String id) {
    String resource = getResource(url);
    ConceptMap conceptMap = FhirMapper.fromJson(resource, ConceptMap.class);
    if (!ResourceType.conceptMap.equals(conceptMap.getResourceType())) {
      throw ApiError.TE107.toApiException();
    }
    conceptMap.setId(id);
    importMapSet(conceptMap);
  }

  private String getResource(String url) {
    log.info("Loading fhir map set from {}", url);
    return new String(client.GET(url).body(), StandardCharsets.UTF_8);
  }

  public void importMapSet(ConceptMap fhirConceptMap) {
    MapSet mapSet = mapper.mapMapSet(fhirConceptMap);
    List<AssociationType> associationTypes = ConceptMapFhirImportMapper.mapAssociationTypes(fhirConceptMap);
    mapSetImportService.importMapSet(mapSet, associationTypes);
  }

}
