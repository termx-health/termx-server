package com.kodality.termx.core.fhir.provenance;

import com.kodality.kefhir.core.exception.FhirException;
import com.kodality.kefhir.core.model.ResourceVersion;
import com.kodality.kefhir.core.model.VersionId;
import com.kodality.kefhir.core.model.search.SearchCriterion;
import com.kodality.kefhir.core.model.search.SearchResult;
import com.kodality.kefhir.structure.api.ResourceContent;
import com.kodality.termx.core.fhir.BaseFhirMapper;
import com.kodality.termx.core.fhir.BaseFhirResourceStorage;
import com.kodality.termx.core.sys.provenance.Provenance;
import com.kodality.termx.core.sys.provenance.ProvenanceService;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.hl7.fhir.r5.model.OperationOutcome.IssueType;


@Singleton
@RequiredArgsConstructor
public class ProvenanceFhirResourceStorage extends BaseFhirResourceStorage {
  private final ProvenanceService provenanceService;

  @Override
  public String getResourceType() {
    return "Provenance";
  }

  @Override
  public ResourceVersion load(String id) {
    throw new UnsupportedOperationException();
  }

  @Override
  public SearchResult search(SearchCriterion criteria) {
    Map<String, String> params = BaseFhirMapper.getSimpleParams(criteria);
    String target = params.get("target");
    if (target == null || !target.contains("/")) {
      throw new FhirException(400, IssueType.INVALID, "expecting 'target' param in format of '{ResourceType}/{id}'");
    }
    target = target.replace("/", "|");
    List<Provenance> result = provenanceService.find(target);
    return new SearchResult(result.size(), result.stream().map(p -> toFhir(p)).toList());
  }

  private ResourceVersion toFhir(Provenance p) {
    return p == null ? null :
        new ResourceVersion(new VersionId("Provenance", p.getId().toString()), new ResourceContent(ProvenanceFhirMapper.toFhirJson(p), "json"));
  }
}
