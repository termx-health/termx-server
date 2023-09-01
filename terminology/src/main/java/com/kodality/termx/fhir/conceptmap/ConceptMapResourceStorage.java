package com.kodality.termx.fhir.conceptmap;

import com.kodality.kefhir.core.exception.FhirException;
import com.kodality.kefhir.core.model.ResourceVersion;
import com.kodality.kefhir.core.model.VersionId;
import com.kodality.kefhir.structure.api.ResourceContent;
import com.kodality.termx.fhir.BaseFhirResourceStorage;
import com.kodality.termx.fhir.codesystem.CodeSystemFhirMapper;
import com.kodality.termx.sys.provenance.Provenance;
import com.kodality.termx.sys.provenance.ProvenanceService;
import com.kodality.termx.terminology.mapset.MapSetService;
import com.kodality.termx.terminology.mapset.association.MapSetAssociationService;
import com.kodality.termx.terminology.mapset.version.MapSetVersionService;
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

    List<MapSetAssociation> associations = mapSetAssociationService.query(new MapSetAssociationQueryParams().setMapSetVersionId(version.getId()).all()).getData();
    version.setAssociations(associations);
    return toFhir(mapSet, version);
  }

  private ResourceVersion toFhir(MapSet ms, MapSetVersion msv) {
    List<Provenance> provenances = provenanceService.find("MapSetVersion|" + msv.getId());
    return ms == null ? null : new ResourceVersion(
        new VersionId("ConceptMap", ConceptMapFhirMapper.toFhirId(ms, msv)),
        new ResourceContent(mapper.toFhirJson(ms, msv, provenances), "json")
    );
  }

}
