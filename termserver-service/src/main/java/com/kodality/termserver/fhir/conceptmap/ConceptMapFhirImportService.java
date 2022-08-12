package com.kodality.termserver.fhir.conceptmap;

import com.kodality.termserver.ApiError;
import com.kodality.termserver.association.AssociationType;
import com.kodality.termserver.codesystem.CodeSystemEntityVersion;
import com.kodality.termserver.codesystem.CodeSystemEntityVersionQueryParams;
import com.kodality.termserver.common.BinaryHttpClient;
import com.kodality.termserver.common.MapSetImportService;
import com.kodality.termserver.mapset.MapSet;
import com.kodality.termserver.mapset.MapSetAssociation;
import com.kodality.termserver.ts.codesystem.entity.CodeSystemEntityVersionService;
import com.kodality.termserver.ts.valueset.ValueSetService;
import com.kodality.termserver.valueset.ValueSet;
import com.kodality.termserver.valueset.ValueSetQueryParams;
import com.kodality.zmei.fhir.FhirMapper;
import com.kodality.zmei.fhir.resource.ResourceType;
import com.kodality.zmei.fhir.resource.infrastructure.Parameters;
import com.kodality.zmei.fhir.resource.infrastructure.Parameters.Parameter;
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
  private final ValueSetService valueSetService;
  private final MapSetImportService mapSetImportService;
  private final CodeSystemEntityVersionService codeSystemEntityVersionService;
  private final BinaryHttpClient client = new BinaryHttpClient();

  public void importMapSets(Parameters parameters, List<String> successes, List<String> warnings) {
    List<String> urls = CollectionUtils.isNotEmpty(parameters.getParameter()) ?
        parameters.getParameter().stream().filter(p -> "url".equals(p.getName())).map(Parameter::getValueString).toList() : Collections.emptyList();
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
    MapSet mapSet = prepareMapSet(ConceptMapFhirImportMapper.mapMapSet(conceptMap));
    List<AssociationType> associationTypes = ConceptMapFhirImportMapper.mapAssociationTypes(conceptMap);
    mapSetImportService.importMapSet(mapSet, associationTypes, false);
  }

  private String getResource(String url) {
    log.info("Loading fhir map set from {}", url);
    return new String(client.GET(url).body(), StandardCharsets.ISO_8859_1);
  }

  private MapSet prepareMapSet(MapSet mapSet) {
    mapSet.setSourceValueSet(findValueSet(mapSet.getSourceValueSet()));
    mapSet.setTargetValueSet(findValueSet(mapSet.getTargetValueSet()));
    prepareAssociations(mapSet.getAssociations());
    return mapSet;
  }

  private void prepareAssociations(List<MapSetAssociation> associations) {
    associations.forEach(association -> {
      association.setSource(findEntityVersion(association.getSource().getCodeSystem(), association.getSource().getCodeSystemVersion(), association.getSource().getCode()));
      association.setTarget(findEntityVersion(association.getTarget().getCodeSystem(), association.getTarget().getCodeSystemVersion(), association.getTarget().getCode()));
    });
  }

  private String findValueSet(String uri) {
    if (uri == null) {
      return null;
    }
    ValueSetQueryParams params = new ValueSetQueryParams();
    params.setUri(uri);
    params.setLimit(1);
    return valueSetService.query(params).findFirst().map(ValueSet::getId).orElse(null);
  }

  private CodeSystemEntityVersion findEntityVersion(String uri, String version, String code) {
    CodeSystemEntityVersionQueryParams params = new CodeSystemEntityVersionQueryParams();
    params.setCodeSystemUri(uri);
    params.setCodeSystemVersion(version);
    params.setCode(code);
    params.setLimit(1);
    return codeSystemEntityVersionService.query(params).findFirst().orElse(null);
  }
}
