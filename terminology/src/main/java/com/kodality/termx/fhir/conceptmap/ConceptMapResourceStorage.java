package com.kodality.termx.fhir.conceptmap;

import com.kodality.kefhir.core.exception.FhirException;
import com.kodality.kefhir.core.model.ResourceVersion;
import com.kodality.kefhir.core.model.VersionId;
import com.kodality.kefhir.structure.api.ResourceContent;
import com.kodality.termx.fhir.BaseFhirResourceStorage;
import com.kodality.termx.fhir.codesystem.CodeSystemFhirMapper;
import com.kodality.termx.terminology.mapset.MapSetService;
import com.kodality.termx.terminology.mapset.MapSetVersionService;
import com.kodality.termx.ts.mapset.MapSet;
import com.kodality.termx.ts.mapset.MapSetQueryParams;
import com.kodality.termx.ts.mapset.MapSetVersion;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.hl7.fhir.r5.model.OperationOutcome.IssueType;

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
  public ResourceVersion load(String fhirId) {
    String[] idParts = CodeSystemFhirMapper.parseCompositeId(fhirId);
    return load(idParts[0], idParts[1]);
  }

  public ResourceVersion load(String id, String versionNumber) {
    MapSetQueryParams mapSetQueryParams = new MapSetQueryParams();
    mapSetQueryParams.setId(id);
    mapSetQueryParams.setAssociationsDecorated(true);
    mapSetQueryParams.setVersionsDecorated(true);
    mapSetQueryParams.setLimit(1);
    MapSet mapSet = mapSetService.query(mapSetQueryParams).findFirst().orElse(null);
    if (mapSet == null) {
      return null;
    }
    MapSetVersion version = versionNumber == null ? mapSetVersionService.loadLastVersion(id) :
        mapSetVersionService.load(id, versionNumber).orElseThrow(() -> new FhirException(400, IssueType.NOTFOUND, "resource not found"));
    return toFhir(mapSet, version);
  }

  private ResourceVersion toFhir(MapSet mapSet, MapSetVersion version) {
    return mapSet == null ? null : new ResourceVersion(
        new VersionId("ConceptMap", ConceptMapFhirMapper.toFhirId(mapSet, version)),
        new ResourceContent(mapper.toFhirJson(mapSet, version), "json")
    );
  }

}
