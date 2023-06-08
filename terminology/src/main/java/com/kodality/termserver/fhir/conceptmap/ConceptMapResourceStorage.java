package com.kodality.termserver.fhir.conceptmap;

import com.kodality.kefhir.core.model.ResourceVersion;
import com.kodality.kefhir.core.model.VersionId;
import com.kodality.kefhir.core.model.search.SearchCriterion;
import com.kodality.kefhir.core.model.search.SearchResult;
import com.kodality.kefhir.structure.api.ResourceContent;
import com.kodality.termserver.fhir.BaseFhirResourceStorage;
import com.kodality.termserver.terminology.mapset.MapSetService;
import com.kodality.termserver.terminology.mapset.MapSetVersionService;
import com.kodality.termserver.ts.mapset.MapSet;
import com.kodality.termserver.ts.mapset.MapSetQueryParams;
import com.kodality.termserver.ts.mapset.MapSetVersion;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class ConceptMapResourceStorage extends BaseFhirResourceStorage {
  private final MapSetService mapSetService;
  private final MapSetVersionService mapSetVersionService;
  private final ConceptMapFhirMapper mapper;

  @Override
  public String getResourceType() {
    return "ConceptMap";
  }

  @Override
  public ResourceVersion load(String id) {
    MapSetQueryParams mapSetQueryParams = new MapSetQueryParams();
    mapSetQueryParams.setId(id);
    mapSetQueryParams.setAssociationsDecorated(true);
    mapSetQueryParams.setVersionsDecorated(true);
    mapSetQueryParams.setLimit(1);
    MapSet mapSet = mapSetService.query(mapSetQueryParams).findFirst().orElse(null);
    if (mapSet == null) {
      return null;
    }
    MapSetVersion version = mapSetVersionService.loadLastVersion(id);
    return toFhir(mapSet, version);
  }

  @Override
  public SearchResult search(SearchCriterion criteria) {
    throw new UnsupportedOperationException();
  }

  private ResourceVersion toFhir(MapSet mapSet, MapSetVersion version) {
    return mapSet == null ? null :
        new ResourceVersion(new VersionId("ConceptMap", mapSet.getId()), new ResourceContent(mapper.toFhirJson(mapSet, version), "json"));
  }

}
