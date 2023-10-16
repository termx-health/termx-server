package com.kodality.termx.terminology.fhir.conceptmap;

import com.kodality.commons.model.QueryResult;
import com.kodality.kefhir.core.exception.FhirException;
import com.kodality.kefhir.core.model.ResourceVersion;
import com.kodality.kefhir.core.model.VersionId;
import com.kodality.kefhir.core.model.search.SearchCriterion;
import com.kodality.kefhir.core.model.search.SearchResult;
import com.kodality.kefhir.structure.api.ResourceContent;
import com.kodality.termx.core.fhir.BaseFhirResourceStorage;
import com.kodality.termx.terminology.fhir.codesystem.CodeSystemFhirMapper;
import com.kodality.termx.core.sys.provenance.Provenance;
import com.kodality.termx.core.sys.provenance.ProvenanceService;
import com.kodality.termx.terminology.terminology.mapset.MapSetService;
import com.kodality.termx.terminology.terminology.mapset.association.MapSetAssociationService;
import com.kodality.termx.terminology.terminology.mapset.version.MapSetVersionService;
import com.kodality.termx.ts.mapset.MapSet;
import com.kodality.termx.ts.mapset.MapSetAssociation;
import com.kodality.termx.ts.mapset.MapSetAssociationQueryParams;
import com.kodality.termx.ts.mapset.MapSetQueryParams;
import com.kodality.termx.ts.mapset.MapSetVersion;
import jakarta.inject.Singleton;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hl7.fhir.r5.model.OperationOutcome.IssueType;

@Singleton
@RequiredArgsConstructor
public class ConceptMapResourceStorage extends BaseFhirResourceStorage {
  private final MapSetService mapSetService;
  private final MapSetVersionService mapSetVersionService;
  private final MapSetAssociationService mapSetAssociationService;
  private final ProvenanceService provenanceService;
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
    mapSetQueryParams.setVersionsDecorated(true);
    mapSetQueryParams.setLimit(1);
    MapSet mapSet = mapSetService.query(mapSetQueryParams).findFirst().orElse(null);
    if (mapSet == null) {
      return null;
    }
    MapSetVersion version = versionNumber == null ? mapSetVersionService.loadLastVersion(id) :
        mapSetVersionService.load(id, versionNumber).orElseThrow(() -> new FhirException(400, IssueType.NOTFOUND, "resource not found"));
    version.setAssociations(loadAssociations(version));
    return toFhir(mapSet, version);
  }

  @Override
  public SearchResult search(SearchCriterion criteria) {
    QueryResult<MapSet> result = mapSetService.query(ConceptMapFhirMapper.fromFhir(criteria));
    return new SearchResult(result.getMeta().getTotal(), result.getData().stream().flatMap(ms -> ms.getVersions().stream().map(msv -> {
      msv.setAssociations(loadAssociations(msv));
      return toFhir(ms, msv);
    })).toList());
  }


  private List<MapSetAssociation> loadAssociations(MapSetVersion version) {
    if (version == null) {
      return List.of();
    }
    return mapSetAssociationService.query(new MapSetAssociationQueryParams().setMapSetVersionId(version.getId()).all()).getData();
  }


  private ResourceVersion toFhir(MapSet ms, MapSetVersion msv) {
    List<Provenance> provenances = provenanceService.find("MapSetVersion|" + msv.getId());
    return ms == null ? null : new ResourceVersion(
        new VersionId("ConceptMap", ConceptMapFhirMapper.toFhirId(ms, msv)),
        new ResourceContent(mapper.toFhirJson(ms, msv, provenances), "json")
    );
  }

}
