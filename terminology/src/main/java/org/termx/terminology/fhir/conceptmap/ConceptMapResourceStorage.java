package org.termx.terminology.fhir.conceptmap;

import com.kodality.commons.model.QueryResult;
import com.kodality.kefhir.core.exception.FhirException;
import com.kodality.kefhir.core.model.ResourceVersion;
import com.kodality.kefhir.core.model.VersionId;
import com.kodality.kefhir.core.model.search.SearchCriterion;
import com.kodality.kefhir.core.model.search.SearchResult;
import com.kodality.kefhir.structure.api.ResourceContent;
import org.termx.core.fhir.BaseFhirResourceHandler;
import org.termx.core.sys.provenance.Provenance;
import org.termx.core.sys.provenance.ProvenanceService;
import org.termx.terminology.fhir.codesystem.CodeSystemFhirMapper;
import org.termx.terminology.terminology.mapset.MapSetService;
import org.termx.terminology.terminology.mapset.association.MapSetAssociationService;
import org.termx.terminology.terminology.mapset.version.MapSetVersionService;
import org.termx.ts.mapset.MapSet;
import org.termx.ts.mapset.MapSetAssociation;
import org.termx.ts.mapset.MapSetAssociationQueryParams;
import org.termx.ts.mapset.MapSetQueryParams;
import org.termx.ts.mapset.MapSetVersion;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;
import java.util.List;
import org.hl7.fhir.r5.model.OperationOutcome.IssueType;

@Singleton
public class ConceptMapResourceStorage extends BaseFhirResourceHandler {
  private final MapSetService mapSetService;
  private final MapSetVersionService mapSetVersionService;
  private final MapSetAssociationService mapSetAssociationService;
  private final ProvenanceService provenanceService;
  private final ConceptMapFhirMapper mapper;
  private final String defaultSearchSummary;

  public ConceptMapResourceStorage(MapSetService mapSetService,
                                   MapSetVersionService mapSetVersionService,
                                   MapSetAssociationService mapSetAssociationService,
                                   ProvenanceService provenanceService,
                                   ConceptMapFhirMapper mapper,
                                   @Value("${termx.fhir.conceptmap.search.default-summary:true}") String defaultSearchSummary) {
    this.mapSetService = mapSetService;
    this.mapSetVersionService = mapSetVersionService;
    this.mapSetAssociationService = mapSetAssociationService;
    this.provenanceService = provenanceService;
    this.mapper = mapper;
    this.defaultSearchSummary = defaultSearchSummary;
  }

  @Override
  public String getResourceType() {
    return "ConceptMap";
  }

  @Override
  public String getPrivilegeName() {
    return "MapSet";
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
        mapSetVersionService.load(id, versionNumber).orElseThrow(() -> new FhirException(404, IssueType.NOTFOUND, "resource not found"));
    // Skip the (potentially-large) per-association query when the caller asked for a
    // lightweight summary. Same pattern as the search path at line 86, and the matching
    // CodeSystem and ValueSet read-side fixes. Without entities the resulting FHIR
    // ConceptMap drops `group.element[]` — exactly what kefhir's post-load summary
    // processor would strip for ?_summary=true anyway.
    boolean lightweight = isCurrentRequestLightweightSummary();
    version.setAssociations(lightweight ? List.of() : loadAssociations(version));
    return toFhir(mapSet, version);
  }

  @Override
  public SearchResult search(SearchCriterion criteria) {
    QueryResult<MapSet> result = mapSetService.query(ConceptMapFhirMapper.fromFhir(criteria));
    boolean lightweight = isLightweightSummary(criteria.getSummary() != null ? criteria.getSummary() : defaultSearchSummary);
    return new SearchResult(result.getMeta().getTotal(), result.getData().stream().flatMap(ms -> ms.getVersions().stream().map(msv -> {
      msv.setAssociations(lightweight ? List.of() : loadAssociations(msv));
      return toFhir(ms, msv);
    })).toList());
  }

  private static boolean isLightweightSummary(String summary) {
    return summary != null && !summary.isBlank() && !"false".equalsIgnoreCase(summary);
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
