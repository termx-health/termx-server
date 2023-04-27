package com.kodality.termserver.fhir.conceptmap;

import com.kodality.termserver.BinaryHttpClient;
import com.kodality.termserver.exception.ApiError;
import com.kodality.termserver.terminology.codesystem.entity.CodeSystemEntityVersionService;
import com.kodality.termserver.terminology.mapset.MapSetImportService;
import com.kodality.termserver.terminology.valueset.ValueSetService;
import com.kodality.termserver.ts.association.AssociationType;
import com.kodality.termserver.ts.codesystem.CodeSystemEntityVersion;
import com.kodality.termserver.ts.codesystem.CodeSystemEntityVersionQueryParams;
import com.kodality.termserver.ts.mapset.MapSet;
import com.kodality.termserver.ts.mapset.MapSetAssociation;
import com.kodality.termserver.ts.valueset.ValueSet;
import com.kodality.termserver.ts.valueset.ValueSetQueryParams;
import com.kodality.zmei.fhir.FhirMapper;
import com.kodality.zmei.fhir.resource.ResourceType;
import com.kodality.zmei.fhir.resource.other.Parameters;
import com.kodality.zmei.fhir.resource.other.Parameters.ParametersParameter;
import com.kodality.zmei.fhir.resource.terminology.ConceptMap;
import io.micronaut.core.util.CollectionUtils;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import javax.inject.Singleton;
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

  public void importMapSets(Parameters parameters, List<String> successes, List<String> warnings) {
    List<String> urls = CollectionUtils.isNotEmpty(parameters.getParameter()) ?
        parameters.getParameter().stream().filter(p -> "url".equals(p.getName())).map(ParametersParameter::getValueString).toList() : Collections.emptyList();
    if (urls.isEmpty()) {
      throw ApiError.TE106.toApiException();
    }
    urls.forEach(url -> {
      try {
        importMapSet(url);
        successes.add(String.format("ConceptMap from resource %s imported", url));
      } catch (Exception e) {
        warnings.add(String.format("ConceptMap from resource %s was not imported due to error: %s", url, e.getMessage()));
      }
    });
  }

  @Transactional
  public void importMapSet(String url) {
    String resource = getResource(url);
    ConceptMap conceptMap = FhirMapper.fromJson(resource, ConceptMap.class);
    if (!ResourceType.conceptMap.equals(conceptMap.getResourceType())) {
      throw ApiError.TE107.toApiException();
    }
    importMapSet(conceptMap);
  }

  @Transactional
  public void importMapSet(com.kodality.zmei.fhir.resource.terminology.ConceptMap fhirConceptMap) {
    MapSet mapSet = mapper.mapMapSet(fhirConceptMap);
    List<AssociationType> associationTypes = ConceptMapFhirImportMapper.mapAssociationTypes(fhirConceptMap);
    mapSetImportService.importMapSet(mapSet, associationTypes);
  }

  private String getResource(String url) {
    log.info("Loading fhir map set from {}", url);
    return new String(client.GET(url).body(), StandardCharsets.UTF_8);
  }
}
