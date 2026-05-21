package org.termx.terminology.fhir.valueset;

import com.kodality.commons.model.QueryResult;
import com.kodality.kefhir.core.exception.FhirException;
import com.kodality.kefhir.core.model.ResourceId;
import com.kodality.kefhir.core.model.ResourceVersion;
import com.kodality.kefhir.core.model.VersionId;
import com.kodality.kefhir.core.model.search.SearchCriterion;
import com.kodality.kefhir.core.model.search.SearchResult;
import com.kodality.kefhir.structure.api.ResourceContent;
import org.termx.core.fhir.BaseFhirResourceHandler;
import org.termx.core.sys.provenance.Provenance;
import org.termx.core.sys.provenance.ProvenanceService;
import org.termx.terminology.terminology.valueset.ValueSetService;
import org.termx.terminology.terminology.valueset.snapshot.ValueSetSnapshotService;
import org.termx.terminology.terminology.valueset.version.ValueSetVersionService;
import org.termx.ts.valueset.ValueSet;
import org.termx.ts.valueset.ValueSetQueryParams;
import org.termx.ts.valueset.ValueSetVersion;
import org.termx.ts.valueset.ValueSetVersionRuleSet;
import com.kodality.zmei.fhir.FhirMapper;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Optional;
import org.hl7.fhir.r5.model.OperationOutcome.IssueType;

@Singleton
public class ValueSetResourceStorage extends BaseFhirResourceHandler {
  private final ValueSetService valueSetService;
  private final ValueSetVersionService valueSetVersionService;
  private final ValueSetFhirImportService importService;
  private final ProvenanceService provenanceService;
  private final ValueSetSnapshotService snapshotService;
  private final ValueSetFhirMapper mapper;
  private final String defaultSearchSummary;

  public ValueSetResourceStorage(ValueSetService valueSetService,
                                 ValueSetVersionService valueSetVersionService,
                                 ValueSetFhirImportService importService,
                                 ProvenanceService provenanceService,
                                 ValueSetSnapshotService snapshotService,
                                 ValueSetFhirMapper mapper,
                                 @Value("${termx.fhir.valueset.search.default-summary:true}") String defaultSearchSummary) {
    this.valueSetService = valueSetService;
    this.valueSetVersionService = valueSetVersionService;
    this.importService = importService;
    this.provenanceService = provenanceService;
    this.snapshotService = snapshotService;
    this.mapper = mapper;
    this.defaultSearchSummary = defaultSearchSummary;
  }

  @Override
  public String getResourceType() {
    return "ValueSet";
  }

  @Override
  public String getPrivilegeName() {
    return "ValueSet";
  }

  @Override
  public ResourceVersion load(String fhirId) {
    String[] idParts = ValueSetFhirMapper.parseCompositeId(fhirId);
    String vsId = idParts[0];
    String versionNumber = idParts[1];

    return load(vsId, versionNumber);
  }

  private ResourceVersion load(String vsId, String versionNumber) {
    ValueSetQueryParams valueSetParams = new ValueSetQueryParams();
    valueSetParams.setId(vsId);
    valueSetParams.setLimit(1);
    ValueSet valueSet = valueSetService.query(valueSetParams).findFirst().orElse(null);
    if (valueSet == null) {
      return null;
    }
    ValueSetVersion version = versionNumber == null ? valueSetVersionService.loadLastVersion(vsId) :
        valueSetVersionService.load(vsId, versionNumber).orElseThrow(() -> new FhirException(404, IssueType.NOTFOUND, "resource not found"));
    // Mirror CodeSystemResourceStorage: when the caller asked for a lightweight summary
    // (?_summary=true / text / count), null out the per-rule explicit concept lists so
    // we never serialise a potentially-huge compose.include.concept array that kefhir's
    // post-load summary processor would strip anyway. The search path at line 99 already
    // does this; read had the same gap as CodeSystem until now.
    return toFhir(valueSet, applyLightweight(version, isCurrentRequestLightweightSummary()));
  }

  @Override
  public ResourceVersion save(ResourceId id, ResourceContent content) {
    com.kodality.zmei.fhir.resource.terminology.ValueSet valueSet =
        FhirMapper.fromJson(content.getValue(), com.kodality.zmei.fhir.resource.terminology.ValueSet.class);
    importService.importValueSet(valueSet);
    return load(valueSet.getId(), valueSet.getVersion());
  }

  @Override
  public String generateNewId() {
    return null; //TODO:
  }

  @Override
  public SearchResult search(SearchCriterion criteria) {
    QueryResult<ValueSet> vsResult = valueSetService.query(ValueSetFhirMapper.fromFhir(criteria));
    QueryResult<ValueSetVersion> vsvResult = valueSetVersionService.query(ValueSetFhirMapper.fromFhirVSVersionParams(criteria).limit(0));
    boolean lightweight = isLightweightSummary(criteria.getSummary() != null ? criteria.getSummary() : defaultSearchSummary);
    return new SearchResult(vsvResult.getMeta().getTotal(),
        vsResult.getData().stream().flatMap(vs -> vs.getVersions().stream().map(vsv -> toFhir(vs, applyLightweight(vsv, lightweight)))).toList());
  }

  private static boolean isLightweightSummary(String summary) {
    return summary != null && !summary.isBlank() && !"false".equalsIgnoreCase(summary);
  }

  private static ValueSetVersion applyLightweight(ValueSetVersion vsv, boolean lightweight) {
    if (!lightweight || vsv == null) {
      return vsv;
    }
    Optional.ofNullable(vsv.getRuleSet())
        .map(ValueSetVersionRuleSet::getRules)
        .ifPresent(rules -> rules.forEach(r -> r.setConcepts(null)));
    return vsv;
  }

  private ResourceVersion toFhir(ValueSet vs, ValueSetVersion vsv) {
    List<Provenance> provenances = provenanceService.find("ValueSetVersion|" + vsv.getId());
    // Surface expansion.total when a snapshot exists, WITHOUT loading the (potentially
    // huge) expansion list. Lets ?_summary=false consumers see "there's an expansion of
    // size N" on the open-in-FHIR list link from termx-web without paying for the
    // contains[] payload. Returns null when no snapshot is stored — mapper then emits
    // no expansion block at all.
    Integer expansionCount = vs == null ? null : snapshotService.loadConceptsTotal(vs.getId(), vsv.getId());
    return vs == null ? null : new ResourceVersion(
        new VersionId("ValueSet", ValueSetFhirMapper.toFhirId(vs, vsv)),
        new ResourceContent(mapper.toFhirJson(vs, vsv, provenances, expansionCount), "json")
    );
  }

}
